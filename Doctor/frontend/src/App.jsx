/**
 * FILE: Doctor/frontend/src/App.jsx
 * DỰ ÁN: MedLedger Doctor (BlockchainCB)
 *
 * MỤC TIÊU:
 * - Chia mã nguồn theo từng nhóm nghiệp vụ để dễ đọc, dễ trình bày trước giảng viên.
 * - Chú thích tên hàm, API được gọi và ranh giới phân quyền.
 * - GIỮ NGUYÊN chức năng đang có. Không tự sửa API, JSX, cách tính hash hay chính sách bảo mật.
 *
 * GHI NHỚ LUỒNG:
 * React (App.jsx) -> api.js -> Java Controller -> Service -> Repository -> Supabase.
 * Ở trang chi tiết bệnh án: AnchorVersionButton -> MetaMask -> Sepolia -> Backend xác minh.
 *
 * Các component Blockchain được import từ ./blockchain/; chúng KHÔNG được định nghĩa ở file này.
 */

import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Activity, ArrowLeft, ArrowUpRight, Bell, Check, ChevronLeft, ChevronRight, ClipboardList, FilePlus2, FileText, HeartPulse, LayoutDashboard, LogOut, Menu, Plus, RefreshCw, Search, ShieldCheck, Upload, UserRound, Users, X } from 'lucide-react';
import { api, specialties, setToken } from './api.js';
import DoctorWalletPanel from "./blockchain/DoctorWalletPanel";
import AnchorVersionButton from "./blockchain/AnchorVersionButton";
import AccessGrantRequests from './AccessGrantRequests';
import { SharedRecordHistory, SharedRecordDetail, SharedRecordTable } from './SharedRecordViews';
import SharedClinicalForm from './SharedClinicalForm';
import { useResource } from './useResource';
/**
 * AuthContext: chia sẻ thông tin bác sĩ đã đăng nhập giữa các trang.
 */
const Auth = createContext(null);

// =============================================================================
// PHẦN 1: XÁC THỰC VÀ PHIÊN ĐĂNG NHẬP
// Lưu JWT trong bộ nhớ; API ở ./api.js; quyền thực sự được kiểm tra tại Java Backend.
// =============================================================================

/**
 * Quản lý phiên đăng nhập (JWT) và hồ sơ bác sĩ cho toàn bộ ứng dụng.
 * Luồng: login() gọi api.login() -> lưu token tạm thời -> api.me(); refresh() tải lại hồ sơ; logout() xóa phiên.
 */
export function AuthProvider({ children }) {
    const [user, setUser] = useState(null), [active, setActive] = useState(false), [notice, setNotice] = useState('');
    const expiry = useRef(null);
    const refreshing = useRef(null);
    /**
     * Đăng xuất: dừng đồng hồ hết hạn, xóa JWT khỏi bộ nhớ và đặt user=null.
     * Được gọi khi người dùng nhấn Đăng xuất hoặc token bị hết hạn.
     */
    function logout(message = '') { clearTimeout(expiry.current); setToken(null); setUser(null); setActive(false); setNotice(message); }
    /**
     * Tải lại hồ sơ Doctor từ Backend bằng api.me(), cập nhật quyền APPROVED/PENDING.
     * Chỉ đăng xuất khi Backend trả 401/403; lỗi DB/mạng tạm thời không xóa phiên.
     */
    function refresh() {
        if (refreshing.current) return refreshing.current;
        const pending=api.me().then(me=>{setUser(me);return me;}).catch(e=>{
            if(e.status===401 || e.status===403)logout(e.message);
            throw e;
        }).finally(()=>{if(refreshing.current===pending)refreshing.current=null;});
        refreshing.current=pending;return pending;
    }
    /**
     * Đăng nhập: api.login() -> nhận JWT -> api.me() lấy hồ sơ -> kích hoạt phiên.
     * Giải mã exp chỉ phục vụ đồng hồ phía client; Backend vẫn xác minh token ở mỗi API.
     */
    async function login(body) {
        const result = await api.login(body);
        setToken(result.token);
        try {
            const me = await api.me();
            setUser(me);
            setActive(true);
            setNotice('');
            let ms = 3600000;
            try {
                const exp = JSON.parse(atob(result.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp;
                if (Number.isFinite(exp)) ms = exp * 1000 - Date.now();
            }
            catch { /* API remains authoritative */ }
            clearTimeout(expiry.current);
            expiry.current = setTimeout(() => logout('Phiên đăng nhập đã hết hạn.'), Math.max(0, ms));
        }
        catch (e) {
            logout(e.message);
            throw e;
        }
    }
    useEffect(() => { const expired = () => logout('Phiên đăng nhập đã hết hạn.'); window.addEventListener('doctor:expired', expired); return () => { window.removeEventListener('doctor:expired', expired); clearTimeout(expiry.current); }; }, []);
    useEffect(() => { if (!active)
        return; const update = () => { if (!document.hidden)
        refresh().catch(() => { }); }; const timer = setInterval(update, 30000); window.addEventListener('focus', update); return () => { clearInterval(timer); window.removeEventListener('focus', update); }; }, [active]);
    return <Auth.Provider value={{ user, setUser, login, logout, refresh, notice }}>{children}</Auth.Provider>;
}
/**
 * Hook đọc AuthContext; chỉ gọi bên trong cây AuthProvider.
 */
const useAuth = () => useContext(Auth);
/**
 * Định dạng ngày ISO thành ngày dễ đọc theo khu vực Việt Nam.
 */
const date = value => value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value)) : '—';
/**
 * Lấy ngày hôm nay theo múi giờ máy người dùng để điền form khám bệnh.
 */
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

// =============================================================================
// PHẦN 2: HOOK TẢI DỮ LIỆU VÀ TIỆN ÍCH
// Có cơ chế tải lại và hủy request khi component unmount.
// =============================================================================

/**
 * Hook dùng chung để tải dữ liệu bất đồng bộ và tự cập nhật khi quay lại tab.
 * Luồng: loader(signal) thường là api.patients/api.records/api.record/...; trả về data, loading, error và reload().
 */
export function useLoad(loader, deps = [], live = true) {
    return useResource(loader, deps, live);
}

// =============================================================================
// PHẦN 3: CÁC COMPONENT GIAO DIỆN DÙNG CHUNG
// Không chứa logic phân quyền ở Backend.
// =============================================================================

/**
 * Hiển thị thông báo lỗi từ Backend hoặc từ thao tác người dùng.
 * Luồng: Chỉ render khi children có thông báo lỗi.
 */
function ErrorBox({ children }) {
    return children ? <div className="alert error" role="alert">{children}</div> : null;
}
/**
 * Đổi mã trạng thái Backend/Blockchain thành nhãn tiếng Việt.
 * Luồng: Ví dụ APPROVED/PENDING của Doctor, PREPARED/PENDING/CONFIRMED của giao dịch.
 */
function Status({ value, blockchain = false }) { const text = { APPROVED: 'Đã phê duyệt', PENDING: blockchain ? 'Chờ xác nhận' : 'Chờ phê duyệt', PREPARED: 'Đã chuẩn bị', CONFIRMED: 'Đã xác minh', FAILED: 'Thất bại', REJECTED: 'Bị từ chối', BLOCKED: 'Đã khóa', NOT_SUBMITTED: 'Chưa gửi Blockchain' }; return <span className={`badge ${['APPROVED', 'CONFIRMED'].includes(value) ? 'green' : 'amber'}`}>{text[value] || value}</span>; }
/**
 * Hiển thị trạng thái đang tải dữ liệu.
 * Luồng: Được dùng bởi LoadState khi API đang chạy.
 */
function Loading() {
    return <div className="empty" role="status">
      <RefreshCw className="spin"/> Đang tải dữ liệu…</div>;
}
/**
 * Hiển thị giao diện khi danh sách không có dữ liệu.
 * Luồng: Thường dùng cho danh sách bệnh nhân và hồ sơ khám.
 */
function Empty({ text = 'Chưa có dữ liệu để hiển thị.' }) { return <div className="empty">
  <FileText size={30}/>
  <h3>{text}</h3>
  <p>Dữ liệu sẽ xuất hiện khi có hồ sơ và quyền truy cập phù hợp.</p>
</div>; }
/**
 * Bộ hiển thị chung cho ba trạng thái: đang tải, lỗi, và có dữ liệu.
 * Luồng: Khi lỗi, nút Thử tải lại gọi state.reload().
 */
function LoadState({ state, children }) { return state.loading ? <Loading /> : state.error ? <><ErrorBox>{state.error}</ErrorBox><button className="secondary" onClick={state.reload}>
    <RefreshCw size={16}/>Thử tải lại</button></> : <>{state.warning && <p role="status" className="alert">{state.warning}</p>}{children(state.data)}</>; }
/**
 * Tiêu đề, mô tả và nút thao tác trên từng trang.
 * Luồng: Chỉ phục vụ giao diện, không gọi API.
 */
function PageTitle({ eyebrow = 'KHÔNG GIAN BÁC SĨ', title, description, action }) { return <div className="page-title">
  <div>
    <div className="eyebrow">{eyebrow}</div>
    <h1>{title}</h1>
    <p>{description}</p>
  </div>{action}</div>; }
/**
 * Điều khiển phân trang cho danh sách.
 * Luồng: setPage() đổi trang; useLoad của trang cha tự tải lại API.
 */
function Pagination({ data, page, setPage }) { return <div className="pagination">
  <span>{data.totalElements ?? 0} kết quả · Trang {page + 1}/{Math.max(1, data.totalPages || 0)}</span>
  <div>
    <button aria-label="Trang trước" className="icon-btn" disabled={!page} onClick={() => setPage(page - 1)}>
      <ChevronLeft />
    </button>
    <button aria-label="Trang sau" className="icon-btn" disabled={page + 1 >= (data.totalPages || 0)} onClick={() => setPage(page + 1)}>
      <ChevronRight />
    </button>
  </div>
</div>; }
/**
 * Hiển thị bảng hồ sơ và điều hướng tới trang chi tiết.
 * Luồng: Nhấn HS-xxxx chuyển tới /records/:id; hiển thị version và trạng thái blockchain.
 */
function RecordTable({ data }) { return !data.content?.length ? <Empty text="Chưa có hồ sơ khám"/> : <div className="table-wrap">
  <table>
    <thead>
      <tr>
        <th>Hồ sơ</th>
        <th>Bệnh nhân</th>
        <th>Ngày khám</th>
        <th>Phiên bản</th>
        <th>Bằng chứng</th>
        <th />
      </tr>
    </thead>
    <tbody>{data.content.map(r => <tr key={r.id}>
        <td>
          <Link className="strong" to={`/records/${r.id}`}>HS-{String(r.id).padStart(4, '0')}</Link>
          <small>Cập nhật {date(r.updatedAt)}</small>
        </td>
        <td>BN-{r.patientId}</td>
        <td>{date(r.version?.examDate)}</td>
        <td>
          <span className="version">v{r.currentVersion}</span>
        </td>
        <td>
          <Status value={r.version?.blockchainStatus || 'NOT_SUBMITTED'} blockchain/>
        </td>
        <td>
          <Link aria-label={`Xem hồ sơ ${r.id}`} to={`/records/${r.id}`}>
            <ArrowUpRight size={18}/>
          </Link>
        </td>
      </tr>)}</tbody>
  </table>
</div>; }

// =============================================================================
// PHẦN 4: ĐĂNG KÝ VÀ ĐĂNG NHẬP
// Trang công khai: /register, /login.
// =============================================================================

/**
 * Trang Đăng ký / Đăng nhập Doctor dùng chung một form.
 * Luồng: Đăng ký gọi api.register(); đăng nhập gọi AuthProvider.login() -> api.login() và api.me().
 */
export function AuthPage({ register = false }) {
    const { login, user, notice } = useAuth();
    const navigate = useNavigate();
    const [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
    if (user)
        return <Navigate to="/" replace/>;
    /**
     * Xử lý form Đăng ký/Đăng nhập; nhánh register dùng api.register(), còn lại gọi login().
     */ //xu ly login/register
    async function submit(e) {
        e.preventDefault();
        setError('');
        setSuccess('');
        setBusy(true);
        const form = e.currentTarget;
        const values = Object.fromEntries(new FormData(form));
        // Remove the visible form copy immediately, including while the request is pending.
        // This cannot erase the browser's Network capture or securely wipe JS strings.
        form.elements.namedItem('password').value = '';
        try {
            if (register) {
                if (new TextEncoder().encode(values.password).length > 72)
                    throw new Error('Mật khẩu tối đa 72 byte UTF-8.');
                await api.register({ ...values, specialtyId: Number(values.specialtyId) });
                setSuccess('Đăng ký thành công. Tài khoản đang chờ Admin phê duyệt. Bạn có thể đăng nhập để xem trạng thái.');
                form.reset();
            }
            else {
                await login(values);
                navigate('/', { replace: true });
            }
        }
        catch (e) {
            setError(e.message);
        }
        finally {
            delete values.password;
            setBusy(false);
        }
    }
    return <main className="auth-layout">
      <section className="auth-story">
        <div className="brand">
          <HeartPulse /> MedLedger <span>DOCTOR</span>
        </div>
        <div>
          <div className="eyebrow light">CHĂM SÓC TỐT HƠN, MỖI NGÀY</div>
          <h1>Kết nối thông tin.<br />Tiếp nối chăm sóc.</h1>
          <p>Không gian làm việc dành cho bác sĩ.<br />Hồ sơ rõ ràng, lịch sử liền mạch, truy cập đúng quyền.</p>
          <div className="pulse-art">
            <HeartPulse size={100}/>
          </div>
          <div className="story-card">
            <ShieldCheck />
            <div>
              <b>Dữ liệu được bảo vệ</b>
              <p>Chỉ truy cập hồ sơ khi bệnh nhân cấp quyền còn hiệu lực.</p>
            </div>
          </div>
        </div>
        <small>BLOCKCHAINCB · ELECTRONIC HEALTH RECORDS</small>
      </section>
      <section className="auth-form">
        <div className="auth-box">
          <span className="eyebrow">CHÀO MỪNG BẠN</span>
          <h1>{register ? 'Đăng ký bác sĩ' : 'Đăng nhập'}</h1>
          <p>{register ? 'Bắt đầu với hồ sơ hành nghề của bạn.' : 'Tiếp tục công việc chăm sóc bệnh nhân.'}</p>
          <ErrorBox>{error || notice}</ErrorBox>{success && <div className="alert success" role="status">{success} <Link to="/login">Đăng nhập →</Link>
          </div>}<form onSubmit={submit}>
    {register && <label>Họ và tên<input name="fullName" autoComplete="name" required maxLength={255}/>
            </label>}
    <label>Email<input type="email" name="email" autoComplete="email" required maxLength={255} placeholder="bacsi@example.com"/>
            </label>
            <label>Mật khẩu<input type="password" name="password" autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 8 : undefined} maxLength={72}/>
            </label>
    {register && <><label>Chứng chỉ hành nghề<input name="licenseNumber" required maxLength={255}/>
              </label><label>Nơi công tác<input name="hospital" required maxLength={255}/>
              </label><label>Chuyên khoa<select name="specialtyId" required>
                  <option value="">Chọn chuyên khoa</option>{specialties.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select>
              </label><small>Danh mục theo dữ liệu nhóm đã cung cấp. Backend kiểm tra chuyên khoa tồn tại.</small></>}
    <button disabled={busy}>{busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản bác sĩ' : 'Đăng nhập vào không gian bác sĩ'}<ArrowUpRight size={17}/>
            </button>
          </form>
          <p>{register ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'} <Link to={register ? '/login' : '/register'}>{register ? 'Đăng nhập' : 'Đăng ký bác sĩ'}</Link>
          </p>
          <div className="auth-note">
            <ShieldCheck size={17}/> Phiên đăng nhập chỉ được giữ trong bộ nhớ. Tải lại trang cần đăng nhập lại.</div>
        </div>
      </section>
    </main>;
}

// =============================================================================
// PHẦN 5: BỐ CỤC VÀ ĐỊNH TUYẾN
// Chặn điều hướng ở UI không thay thế bảo vệ API bằng JWT và policy.
// =============================================================================

/**
 * Khung giao diện sau đăng nhập: sidebar, thanh đầu trang, footer và quyền điều hướng.
 * Luồng: Chỉ tài khoản APPROVED được mở các route nghiệp vụ; chưa duyệt chỉ xem trạng thái và hồ sơ.
 */
function Shell() {
    const { user, logout } = useAuth();
    const [open, setOpen] = useState(false);
    const approved = user?.approvalStatus === 'APPROVED';
    return <div className="app-layout">
      <aside className={open ? 'sidebar open' : 'sidebar'}>
        <Link to="/" className="brand">
          <HeartPulse /> MedLedger <span>DOCTOR</span>
        </Link>
        <div className="sidebar-label">KHÔNG GIAN LÀM VIỆC</div>
        <nav onClick={() => setOpen(false)}>{approved ? <><NavLink end to="/">
              <LayoutDashboard />Tổng quan</NavLink><NavLink to="/patients">
              <Users />Bệnh nhân</NavLink><NavLink to="/records">
              <ClipboardList />Lịch sử hồ sơ</NavLink></> : <NavLink to="/approval">
            <ShieldCheck />Trạng thái tài khoản</NavLink>}<NavLink to="/profile">
            <UserRound />Hồ sơ cá nhân</NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="privacy-card">
            <ShieldCheck />
            <b>Quyền truy cập có kiểm soát</b>
            <p>Mỗi lượt xem hồ sơ đều được Backend kiểm tra và ghi nhật ký.</p>
          </div>
          <button className="logout" onClick={() => logout()}>
            <LogOut size={18}/>Đăng xuất</button>
          <small>BlockchainCB · Doctor workspace</small>
        </div>
      </aside>{open && <button className="backdrop" aria-label="Đóng menu" onClick={() => setOpen(false)}/>}
    <div className="main-area">
        <header>
          <div className="header-left">
            <button className="icon-btn mobile-menu" aria-label="Mở menu" onClick={() => setOpen(!open)}>
              <Menu />
            </button>
            <span>Chăm sóc sức khỏe <b>/ Không gian bác sĩ</b>
            </span>
          </div>
          <div className="header-user">
            <span className="live-dot"/>Phiên bảo mật<span className="avatar">{user.fullName?.slice(0, 1)}</span>
            <div>
              <b>{user.fullName}</b>
              <small>{specialties.find(([id]) => id === user.specialtyId)?.[1] || 'Bác sĩ'}</small>
            </div>
          </div>
        </header>
        <main className="content">
          <Routes>
            <Route path="profile" element={<Profile />}/>
            <Route path="approval" element={<Approval />}/>
            <Route path="*" element={approved ? <ApprovedRoutes /> : <Navigate to="/approval" replace/>}/>
          </Routes>
        </main>
        <footer>MedLedger · Hồ sơ điện tử <span>Dữ liệu off-chain · Blockchain theo trạng thái xác minh</span>
        </footer>
      </div>
    </div>;
}
/**
 * Khai báo các URL nghiệp vụ dành riêng cho Doctor đã APPROVED.
 * Luồng: Route tới Dashboard, Patients, History, RecordForm, RecordDetail, EditRecord.
 */
function ApprovedRoutes() { return <Routes>
  <Route index element={<Dashboard />}/>
  <Route path="patients" element={<Patients />}/>
  <Route path="patients/:patientId" element={<PatientRecords />}/>
  <Route path="records" element={<History />}/>
  <Route path="records/new" element={<CreateRecordRoute />}/>
  <Route path="shared-records/:id/edit" element={<SharedClinicalForm />}/>
  <Route path="records/:id/edit" element={<EditRecord />}/>
  <Route path="records/:id" element={<RecordDetail />}/>
  <Route path="shared-records/:id" element={<SharedRecordDetail />}/>
  <Route path="shared-patients/:patientId" element={<SharedRecordHistory patient/>}/>
  <Route path="*" element={<Empty text="Không tìm thấy trang"/>}/>
</Routes>; }
/**
 * Bộ định tuyến gốc của MedLedger Doctor.
 * Luồng: /login và /register là công khai; còn lại yêu cầu user trong AuthProvider.
 */
export function App() { const { user } = useAuth(); return <Routes>
  <Route path="/login" element={<AuthPage />}/>
  <Route path="/register" element={<AuthPage register/>}/>
  <Route path="/*" element={user ? <Shell /> : <Navigate to="/login" replace/>}/>
</Routes>; }

// =============================================================================
// PHẦN 6: TRẠNG THÁI TÀI KHOẢN VÀ DASHBOARD
// Admin phê duyệt ở module Admin, Doctor chỉ xem trạng thái.
// =============================================================================

/**
 * Màn hình Doctor theo dõi trạng thái chờ Admin duyệt.
 * Luồng: Nút Kiểm tra trạng thái gọi AuthProvider.refresh() -> api.me(); không tự duyệt tài khoản.
 */
function Approval() { const { user, refresh } = useAuth(); const [error, setError] = useState(''), [busy, setBusy] = useState(false); if (user.approvalStatus === 'APPROVED')
    return <Navigate to="/" replace/>; return <><PageTitle title="Trạng thái tài khoản" description="Theo dõi quá trình xét duyệt hồ sơ hành nghề."/><section className="panel approval">
        <div className="approval-icon">
          <ShieldCheck size={38}/>
        </div>
        <Status value={user.approvalStatus}/>
        <h2>{user.approvalStatus === 'REJECTED' ? 'Hồ sơ chưa được chấp thuận' : 'Hồ sơ của bạn đang được xem xét'}</h2>
        <p>{user.approvalStatus === 'REJECTED' ? 'Vui lòng liên hệ Admin để biết lý do và hướng dẫn bổ sung.' : 'Admin cần xác minh thông tin hành nghề trước khi bạn sử dụng nghiệp vụ bệnh án.'}</p>
        <div className="steps">
          <span>
            <Check />Đăng ký tài khoản</span>
          <span>
            <Activity />Admin xét duyệt</span>
          <span>
            <Users />Truy cập đúng quyền</span>
        </div>
        <ErrorBox>{error}</ErrorBox>
        <button disabled={busy} onClick={async () => { setBusy(true); try {
    await refresh();
}
catch (e) {
    setError(e.message);
}
finally {
    setBusy(false);
} }}>
          <RefreshCw size={16}/>Kiểm tra trạng thái</button>
        <Link to="/profile">Xem hồ sơ cá nhân →</Link>
      </section></>; }
/**
 * Trang tổng quan số bệnh nhân và hồ sơ được phép truy cập.
 * Luồng: Gọi song song api.patients() và api.records(); Backend mới quyết định quyền xem.
 */
function Dashboard() { const { user } = useAuth(); const state = useLoad(async (signal) => { const config = await api.integration(signal); const [patients, records] = await Promise.all([api.patients('', 0, signal), config.sharedGrantsEnabled ? (config.sharedHistoryReady ? api.sharedRecords('', 0, signal) : Promise.resolve(null)) : api.records('', 0, signal)]); return { patients, records, shared:config.sharedGrantsEnabled }; }); return <><PageTitle eyebrow="TỔNG QUAN HÔM NAY" title={`Chào bác sĩ ${user.fullName}`} description="Mọi thông tin cần thiết cho một ngày chăm sóc hiệu quả." action={<Link className="button" to="/patients">
    <Plus size={18}/>Chọn bệnh nhân để tạo hồ sơ khám</Link>}/><LoadState state={state}>{({ patients, records, shared }) => <><div className="stats">
        <Stat icon={<Users />} label="Bệnh nhân được cấp quyền" value={patients.totalElements} note="Quyền đọc còn hiệu lực"/>
        <Stat icon={<ClipboardList />} label="Hồ sơ do bạn tạo" value={records ? records.totalElements : 'Chờ tích hợp'} note="Còn quyền xem hiện hành"/>
        <Stat icon={<ShieldCheck />} label="Tài khoản hành nghề" value="Đã duyệt" note="Kiểm tra từ Backend"/>
      </div><div className="dashboard-grid">
        <section className="panel">
          <div className="section-head">
            <div>
              <h2>Hồ sơ gần đây</h2>
              <p>Tiếp nối quá trình khám và điều trị</p>
            </div>
            <Link to="/records">Xem tất cả →</Link>
          </div>
          {records ? (shared ? <SharedRecordTable data={records}/> : <RecordTable data={records}/>) : <Empty text="Bệnh án chung đang chờ xác minh; chưa có dữ liệu hồ sơ để hiển thị."/>}
        </section>
        <aside className="panel care-card">
          <div className="care-art">
            <HeartPulse size={68}/>
          </div>
          <span className="eyebrow">CHĂM SÓC LIỀN MẠCH</span>
          <h2>Mỗi phiên bản,<br />một dấu mốc.</h2>
          <p>Lịch sử được giữ lại khi cập nhật hồ sơ. SHA-256 hỗ trợ kiểm tra nội dung off-chain.</p>
          <Link className="secondary button" to="/patients">Mở danh sách bệnh nhân<ArrowUpRight size={16}/>
          </Link>
          <div className="offchain-note">
            <ShieldCheck />SHA-256 · Xác minh riêng từng phiên bản</div>
        </aside>
      </div><section className="banner">
        <ShieldCheck />
        <div>
          <b>Bệnh nhân luôn giữ quyền kiểm soát</b>
          <p>Quyền có thể được thu hồi bất kỳ lúc nào. Hệ thống kiểm tra lại khi bạn truy cập hồ sơ.</p>
        </div>
      </section></>}</LoadState></>; }
/**
 * Một thẻ chỉ số trong Dashboard.
 * Luồng: Chỉ hiển thị label, số liệu và ghi chú.
 */
function Stat({ icon, label, value, note }) { return <section className="stat">
  <span className="stat-icon">{icon}</span>
  <p>{label}</p>
  <strong>{value ?? '—'}</strong>
  <small>{note}</small>
</section>; }

// =============================================================================
// PHẦN 7: BỆNH NHÂN VÀ LỊCH SỬ HỒ SƠ
// Quyền xem/tạo/sửa bệnh án phải được Java Backend xác minh.
// =============================================================================

/**
 * Danh sách bệnh nhân được cấp quyền đọc.
 * Luồng: Gọi api.patients(query, page, signal); hỗ trợ tìm tên và phân trang.
 */
function Patients() { const [q, setQ] = useState(''), [query, setQuery] = useState(''), [page, setPage] = useState(0); const state = useLoad(s => api.patients(query, page, s), [query, page]); const config = useLoad(s=>api.integration(s),[],false); return <><PageTitle title="Bệnh nhân" description="Chỉ hiển thị bệnh nhân đã cấp quyền đọc còn hiệu lực cho bạn."/><AccessGrantRequests onChanged={state.reload}/><section className="panel">
    <div className="section-head">
      <h2>Danh sách được cấp quyền</h2>
      <form className="search" onSubmit={e => { e.preventDefault(); setPage(0); setQuery(q); }}>
        <Search size={18}/>
        <input aria-label="Tìm bệnh nhân" placeholder="Tìm theo tên bệnh nhân…" maxLength={100} value={q} onChange={e => setQ(e.target.value)}/>
        <button className="secondary">Tìm</button>
      </form>
    </div>
    <LoadState state={state}>{data => <>{data.content.length ? <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Bệnh nhân</th>
                <th>Mã bệnh nhân</th>
                <th>Truy cập</th>
                <th />
              </tr>
            </thead>
            <tbody>{data.content.map(p => <tr key={p.id}>
                <td>
                  <div className="person">
                    <span className="avatar patient">{p.fullName.slice(0, 1)}</span>
                    <b>{p.fullName}</b>
                  </div>
                </td>
                <td>{p.patientCode || `BN-${p.id}`}</td>
                <td>
                  <span className="badge green">Được phép xem</span>
                </td>
                <td>
                  {p.patientCode && config.data?.sharedHistoryReady ? <Link to={`/shared-patients/${p.id}`}>Xem hồ sơ</Link> : p.patientCode ? <span className="muted">Bệnh án chung chờ tích hợp</span> : <Link to={`/patients/${p.id}`}>Xem hồ sơ <ArrowUpRight size={15}/></Link>}
                </td>
              </tr>)}</tbody>
          </table>
        </div> : <Empty text="Chưa có bệnh nhân phù hợp"/>}<Pagination data={data} page={page} setPage={setPage}/></>}</LoadState>
  </section></>; }
/**
 * Danh sách các bệnh án của một bệnh nhân.
 * Luồng: Lấy patientId từ URL và gọi api.patientRecords(); cho phép điều hướng tới form tạo hồ sơ.
 */
function PatientRecords() { const { patientId } = useParams(); const [page, setPage] = useState(0); const state = useLoad(s => api.patientRecords(patientId, page, s), [patientId, page]); return <><Link className="back" to="/patients">
    <ArrowLeft size={16}/>Danh sách bệnh nhân</Link><PageTitle title={`Hồ sơ bệnh nhân BN-${patientId}`} description="Quyền xem, tạo và cập nhật được kiểm tra riêng tại Backend." action={<Link className="button" to={`/records/new?patientId=${patientId}`}>
    <Plus size={16}/>Tạo hồ sơ khám</Link>}/><section className="panel">
    <LoadState state={state}>{data => <><RecordTable data={data}/><Pagination data={data} page={page} setPage={setPage}/></>}</LoadState>
  </section></>; }
/**
 * Danh sách hồ sơ bác sĩ đã tạo và còn quyền đọc.
 * Luồng: Gọi api.records(filter, page, signal); hỗ trợ lọc theo mã bệnh nhân.
 */
function History() { const config=useLoad(s=>api.integration(s),[],false); return <LoadState state={config}>{c=>c.sharedGrantsEnabled?<SharedRecordHistory/>:<LegacyHistory/>}</LoadState>; }
function LegacyHistory() { const [page, setPage] = useState(0), [patient, setPatient] = useState(''), [filter, setFilter] = useState(''); const state = useLoad(s => api.records(filter, page, s), [filter, page]); return <><PageTitle title="Lịch sử hồ sơ" description="Hồ sơ do bạn tạo và vẫn còn quyền đọc hiện hành."/><section className="panel">
    <div className="section-head">
      <h2>Hồ sơ của bạn</h2>
      <form className="search" onSubmit={e => { e.preventDefault(); setFilter(patient); setPage(0); }}>
        <input aria-label="Lọc mã bệnh nhân" type="number" min="1" placeholder="Mã bệnh nhân" value={patient} onChange={e => setPatient(e.target.value)}/>
        <button className="secondary">Lọc</button>
      </form>
    </div>
    <LoadState state={state}>{data => <><RecordTable data={data}/><Pagination data={data} page={page} setPage={setPage}/></>}</LoadState>
  </section></>; }

// =============================================================================
// PHẦN 8: HỒ SƠ CÁ NHÂN VÀ LIÊN KẾT VÍ
// Kết nối ví chỉ lấy địa chỉ; phải ký challenge và Backend xác thực để chứng minh sở hữu ví.
// =============================================================================

/**
 * Hồ sơ cá nhân Doctor và khu vực xác minh/liên kết ví MetaMask.
 * Luồng: Lưu họ tên/nơi công tác qua api.profile(); DoctorWalletPanel xử lý ví và cập nhật walletAddress.
 */
function Profile() { const { user, setUser, refresh } = useAuth(); const [error, setError] = useState(''), [success, setSuccess] = useState(''), [busy, setBusy] = useState(false); return <><PageTitle title="Hồ sơ cá nhân" description="Quản lý thông tin hiển thị và theo dõi trạng thái hành nghề."/><section className="panel profile">
    <div className="profile-cover">
      <span className="avatar large">{user.fullName.slice(0, 1)}</span>
      <div>
        <h2>{user.fullName}</h2>
        <p>{user.email}</p>
        <Status value={user.approvalStatus}/>
      </div>
    </div>
    <ErrorBox>{error}</ErrorBox>{success && <div className="alert success" role="status">{success}</div>}<form onSubmit={async (e) => { e.preventDefault(); setBusy(true); setError(''); setSuccess(''); const body = Object.fromEntries(new FormData(e.currentTarget)); try {
    setUser(await api.profile(body));
    setSuccess('Đã cập nhật thông tin.');
}
catch (e) {
    setError(e.message);
}
finally {
    setBusy(false);
} }}>
      <div className="form-grid">
        <label>Họ và tên<input name="fullName" defaultValue={user.fullName} required maxLength={255}/>
        </label>
        <label>Nơi công tác<input name="hospital" defaultValue={user.hospital} required maxLength={255}/>
        </label>
        <label>Chứng chỉ hành nghề<input value={user.licenseNumber} disabled/>
        </label>
        <label>Chuyên khoa<input value={specialties.find(([id]) => id === user.specialtyId)?.[1] || user.specialtyId} disabled/>
        </label>
      </div>
      <p className="muted">Email, chuyên khoa và chứng chỉ do quy trình tài khoản quản lý.</p>
      <button disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
    </form>
    <div className="wallet">
      <DoctorWalletPanel linkedWallet={user.walletAddress} approvalStatus={user.approvalStatus} onLinked={refresh}/>
    </div>
  </section></>; }

// =============================================================================
// PHẦN 9: TẠO / CẬP NHẬT PHIÊN BẢN BỆNH ÁN
// Mỗi lần sửa tạo phiên bản mới; SHA-256 tính ở Java, không tự tạo trong React.
// =============================================================================

/**
 * Tải bệnh án cũ để mở form tạo phiên bản mới.
 * Luồng: Gọi api.record(id); truyền existing xuống RecordForm.
 */
function EditRecord() { const { id } = useParams(); const state = useLoad(s => api.record(id, s), [id], false); return <LoadState state={state}>{r => <RecordForm key={`${r.id}-${r.currentVersion}`} existing={r}/>}</LoadState>; }
/**
 * Form tạo hồ sơ khám hoặc cập nhật thành phiên bản mới.
 * Luồng: Gọi api.patientRecords() để kiểm tra quyền xem; api.upload() tải tệp; api.create()/api.update() để lưu.
 */
function CreateRecordRoute(){const config=useLoad(s=>api.integration(s),[],false);return <LoadState state={config}>{c=>c.sharedGrantsEnabled?<SharedClinicalForm/>:<RecordForm/>}</LoadState>;}
function RecordForm({ existing }) {
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const initial = existing?.version;
    const [patient, setPatient] = useState(String(existing?.patientId || params.get('patientId') || ''));
    const [files, setFiles] = useState(initial?.attachmentIds || []), [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState(''), [denied, setDenied] = useState(false);
    const [form, setForm] = useState({ examDate: initial?.examDate || today(), diagnosis: initial?.diagnosis || '', prescription: initial?.prescription || '', testResults: initial?.testResults || '', notes: initial?.notes || '' });
    const allowed = useLoad(s => patient ? api.patientRecords(patient, 0, s) : Promise.resolve(null), [patient]);
    useEffect(() => { if (allowed.error) {
        setDenied(true);
        setForm({});
        setFiles([]);
    } }, [allowed.error]);
    /**
     * Xử lý lỗi nghiệp vụ bệnh án; nếu bị từ chối quyền thì xóa dữ liệu form đang nhập.
     */
    function fail(e) { setError(e.message); if (e.status === 403 || e.status === 401) {
        setDenied(true);
        setForm({});
        setFiles([]);
    } }
    /**
     * Tải tệp đính kèm: kiểm tra số lượng, dung lượng, MIME; gọi api.upload().
     * Backend phải kiểm tra lại quyền và an toàn tệp, không tin kiểm tra phía React.
     */
    async function upload(e) { const selected = [...e.target.files]; e.target.value = ''; if (selected.length + files.length > 10) {
        setError('Tối đa 10 tệp mỗi phiên bản.');
        return;
    } setUploading(true); setError(''); try {
        for (const file of selected) {
            if (file.size > 5 * 1024 * 1024 || !['application/pdf', 'image/png', 'image/jpeg'].includes(file.type))
                throw new Error('Chỉ chọn PDF, PNG, JPEG tối đa 5 MiB.');
            const a = await api.upload(patient, file);
            setFiles(old => [...old, a.id]);
        }
    }
    catch (e) {
        fail(e);
    }
    finally {
        setUploading(false);
    } }
    /**
     * Lưu bệnh án: api.create() khi tạo mới, api.update() khi tạo phiên bản tiếp theo.
     * Gửi expectedVersion để Backend kiểm tra xung đột sửa đồng thời; sau khi lưu điều hướng đến /records/:id.
     */
    async function submit(e) { e.preventDefault(); setBusy(true); setError(''); try {
        const content = { ...form, patientId: Number(patient), attachmentIds: files };
        const r = existing ? await api.update(existing.id, { expectedVersion: existing.currentVersion, content }) : await api.create(content);
        navigate(`/records/${r.id}`, { replace: true });
    }
    catch (e) {
        fail(e);
    }
    finally {
        setBusy(false);
    } }
    return <><Link className="back" to={existing ? `/records/${existing.id}` : '/patients'}>
        <ArrowLeft size={16}/>Quay lại</Link><PageTitle title={existing ? 'Tạo phiên bản mới' : 'Tạo hồ sơ khám'} description={existing ? `Giữ nguyên phiên bản v${existing.currentVersion}. Thay đổi được lưu thành phiên bản mới.` : 'Thông tin khám được lưu off-chain, kèm bằng chứng SHA-256.'}/><section className="panel record-form">
        <ErrorBox>{error}</ErrorBox>{denied ? <Empty text="Không thể tiếp tục vì thiếu quyền hiện hành"/> : <form onSubmit={submit}>
          <div className="form-grid">
            <label>Mã bệnh nhân<input type="number" min="1" required value={patient} disabled={!!existing} onChange={e => { setPatient(e.target.value); setFiles([]); }}/>
            </label>
            <label>Ngày khám<input type="date" required max={today()} value={form.examDate} onChange={e => setForm({ ...form, examDate: e.target.value })}/>
            </label>
          </div>{patient && <><ErrorBox>{allowed.error}</ErrorBox>{allowed.loading && <p role="status">Đang kiểm tra quyền đọc…</p>}</>}{[['diagnosis', 'Chẩn đoán'], ['prescription', 'Đơn thuốc'], ['testResults', 'Kết quả xét nghiệm'], ['notes', 'Ghi chú khám bệnh']].map(([key, label]) => <label key={key}>{label}{key === 'diagnosis' ? ' *' : ''}<textarea name={key} required={key === 'diagnosis'} maxLength={10000} rows={key === 'diagnosis' ? 4 : 3} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })}/>
          </label>)}<div className="upload-box">
            <Upload />
            <b>Tệp đính kèm riêng tư</b>
            <p>PDF, PNG, JPEG · 5 MiB/tệp · Tối đa 10 tệp</p>
            <input aria-label="Tải tệp đính kèm" type="file" multiple accept="application/pdf,image/png,image/jpeg" disabled={!patient || allowed.loading || !!allowed.error || uploading} onChange={upload}/>{uploading && <p role="status">Đang tải tệp…</p>}{files.map(id => <div className="file-chip" key={id}>Tệp #{id}<button type="button" aria-label={`Bỏ tệp ${id}`} className="icon-btn" disabled={uploading} onClick={() => setFiles(files.filter(f => f !== id))}>
                <X size={15}/>
              </button>
            </div>)}</div>
          <div className="alert info">Chưa gửi lên Blockchain. Backend kiểm tra quyền tạo/cập nhật khi lưu. Khi lỗi 409, hãy tải lại hồ sơ; khi lỗi 503, kiểm tra lịch sử trước khi gửi lại.</div>
          <div className="form-actions">
            <Link className="secondary button" to={existing ? `/records/${existing.id}` : '/patients'}>Hủy</Link>
            <button disabled={busy || uploading || !patient || allowed.loading || !!allowed.error}>{busy ? 'Đang lưu…' : existing ? 'Lưu phiên bản mới' : 'Lưu hồ sơ khám'}</button>
          </div>
        </form>}</section></>;
}

// =============================================================================
// PHẦN 10: CHI TIẾT, SHA-256 VÀ BLOCKCHAIN
// Blockchain chỉ lưu bằng chứng hash; dữ liệu khám được lưu off-chain.
// =============================================================================

/**
 * Chi tiết hồ sơ: nội dung khám, lịch sử phiên bản, tệp, SHA-256 và giao dịch Blockchain.
 * Luồng: Gọi api.record(), api.versions(), api.version(), api.hash(), api.download(); AnchorVersionButton lo ghi hash on-chain.
 */
function RecordDetail() {
    const { user } = useAuth();
    const { id } = useParams();
    const [page, setPage] = useState(0), [number, setNumber] = useState(null), [downloadError, setDownloadError] = useState('');
    const state = useLoad(async (s) => { const r = await api.record(id, s); const versions = await api.versions(id, page, s); const n = number || r.currentVersion; const v = number ? await api.version(id, n, s) : r.version; const hash = await api.hash(id, n, s); return { r, versions, v, hash }; }, [id, page, number]);
    /**
     * Tải tệp phiên bản: api.download() trả Blob; tạo link tạm để lưu về máy.
     */
    async function download(file, v) { setDownloadError(''); try {
        const blob = await api.download(id, v.versionNumber, file);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `ho-so-${id}-v${v.versionNumber}-tep-${file}`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
    catch (e) {
        setDownloadError(e.message);
        if (e.status === 403)
            state.reload();
    } }
    return <><Link className="back" to="/records">
        <ArrowLeft size={16}/>Lịch sử hồ sơ</Link><PageTitle title={`Hồ sơ HS-${String(id).padStart(4, '0')}`} description="Nội dung khám, lịch sử phiên bản và bằng chứng toàn vẹn."/><LoadState state={state}>{({ r, versions, v, hash }) => <><div className="record-summary panel">
            <div>
              <small>BỆNH NHÂN</small>
              <b>BN-{r.patientId}</b>
            </div>
            <div>
              <small>NGÀY KHÁM</small>
              <b>{date(v.examDate)}</b>
            </div>
            <div>
              <small>PHIÊN BẢN ĐANG XEM</small>
              <b>v{v.versionNumber} / v{r.currentVersion}</b>
            </div>
            <Link className="button" to={`/records/${id}/edit`}>
              <FilePlus2 size={17}/>Tạo phiên bản mới</Link>
          </div><div className="detail-grid">
            <section className="panel medical-content">
              <div className="section-head">
                <h2>Thông tin khám bệnh</h2>
                <span className="version">v{v.versionNumber}</span>
              </div>{[['Chẩn đoán', v.diagnosis], ['Đơn thuốc', v.prescription], ['Kết quả xét nghiệm', v.testResults], ['Ghi chú', v.notes]].map(([label, text]) => <section key={label}>
                <h3>{label}</h3>
                <p className="medical-text">{text || 'Chưa có thông tin'}</p>
              </section>)}<h3>Tệp đính kèm</h3>
              <ErrorBox>{downloadError}</ErrorBox>{v.attachmentIds?.length ? v.attachmentIds.map(file => <button className="secondary" key={file} onClick={() => download(file, v)}>
                <FileText size={16}/>Tải tệp #{file}</button>) : <p className="muted">Không có tệp đính kèm.</p>}</section>
            <aside>
              <section className="panel">
                <div className="section-head">
                  <h2>Lịch sử phiên bản</h2>
                </div>{versions.content.map(item => <button className={`timeline-item ${item.versionNumber === v.versionNumber ? 'selected' : ''}`} key={item.id} onClick={() => setNumber(item.versionNumber)}>
                  <span className="timeline-dot"/>
                  <div>
                    <b>Phiên bản {item.versionNumber}</b>
                    <small>{date(item.createdAt)} · BS-{item.doctorId}</small>
                  </div>
                  <ChevronRight size={16}/>
                </button>)}<Pagination data={versions} page={page} setPage={setPage}/>
              </section>
              <section className="panel hash-panel">
                <ShieldCheck />
                <h2>Bằng chứng SHA-256</h2>
                <Status value={hash.blockchainStatus} blockchain/>
                <p>Hash nội dung phiên bản</p>
                <code>{hash.hash}</code>
                <dl>
                  <dt>Snapshot / hash</dt>
                  <dd>{hash.storedSnapshotMatchesHash ? 'Khớp' : 'Không khớp'}</dd>
                  <dt>Nội dung / hash</dt>
                  <dd>{hash.currentContentMatchesHash ? 'Khớp' : 'Không khớp'}</dd>
                  <dt>Liên kết phiên bản</dt>
                  <dd>{hash.previousLinkMatches ? 'Khớp' : 'Không khớp'}</dd>
                </dl>{(!hash.currentContentMatchesHash || !hash.storedSnapshotMatchesHash || !hash.previousLinkMatches) && <ErrorBox>Phát hiện dữ liệu không toàn vẹn. Liên hệ quản trị viên.</ErrorBox>}<small>Ba cờ trên kiểm tra dữ liệu off-chain. Xem xác minh giao dịch ở phần Ethereum bên dưới.</small>
                <AnchorVersionButton key={`${r.id}-${v.versionNumber}`} recordId={r.id} versionNumber={v.versionNumber} linkedWallet={user.walletAddress} authorAllowed={user.id === v.doctorId} integrity={hash.currentContentMatchesHash && hash.storedSnapshotMatchesHash && hash.previousLinkMatches}/>
              </section>
            </aside>
          </div></>}</LoadState></>;
}
