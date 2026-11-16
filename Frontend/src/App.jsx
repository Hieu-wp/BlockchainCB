import { useEffect, useRef, useState } from 'react'
import {
  BrowserRouter,
  NavLink,
  Link,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom'

import {
  Activity,
  LayoutDashboard,
  Users,
  Stethoscope,
  ScrollText,
  ShieldCheck,
  LogOut,
  Menu,
  X,
  HeartPulse,
  LockKeyhole,
  Mail,
  Eye,
  EyeOff,
  Blocks,
  Server,
  UserRound,
} from 'lucide-react'

// Các trang quản trị
import Dashboard from './pages/Dashboard'
import UsersPage from './pages/Users'
import DoctorsPage from './pages/Doctors'
import AuditLogs from './pages/AuditLogs'
import Transactions from './pages/Transactions'
import AdminProfile from './pages/AdminProfile'
import SystemStatus from './pages/SystemStatus'
import NotFound from './pages/NotFound'
import { HopThoai } from './components/HopThoai'

// Các hàm gọi API Spring Boot
import {
  apiAdmin,
  coQuyenAdmin,
  dangNhap,
  dangXuat,
} from './services/api'

import './App.css'

// =====================================================
// 1. CẤU HÌNH MENU QUẢN TRỊ
// =====================================================

const menu = [
  {
    path: '/',
    name: 'Tổng quan',
    icon: LayoutDashboard,
    end: true,
  },
  {
    path: '/users',
    name: 'Người dùng',
    icon: Users,
  },
  {
    path: '/doctors',
    name: 'Bác sĩ',
    icon: Stethoscope,
  },
  {
    path: '/audit-logs',
    name: 'Nhật ký quản trị',
    icon: ScrollText,
  },
  { path: '/transactions', name: 'Nhật ký giao dịch Blockchain', icon: Blocks },
  { path: '/system-status', name: 'Trạng thái hệ thống', icon: Server },
  { path: '/profile', name: 'Hồ sơ Admin', icon: UserRound },
]

// =====================================================
// 2. GIAO DIỆN ĐĂNG NHẬP ADMIN
// =====================================================

function DangNhap({ onDangNhap, thongBaoPhien }) {
  const [email, setEmail] = useState('')
  const [matKhau, setMatKhau] = useState('')
  const [hienMatKhau, setHienMatKhau] = useState(false)
  const [dangGui, setDangGui] = useState(false)
  const [loi, setLoi] = useState('')

  // Xử lý đăng nhập bằng Spring Security Form Login.
  const xuLyDangNhap = async (suKien) => {
    suKien.preventDefault()

    if (dangGui) return

    setDangGui(true)
    setLoi('')

    try {
      const duLieu = await dangNhap(
        email.trim(),
        matKhau
      )

      // Kiểm tra quyền Admin theo cấu trúc JSON thực tế.
      if (!coQuyenAdmin(duLieu)) {
        throw new Error(
          'Tài khoản không có quyền truy cập ADMIN.'
        )
      }

      // Không giữ mật khẩu trong state sau khi đăng nhập.
      setMatKhau('')

      onDangNhap(duLieu)
    } catch (loiDangNhap) {
      setLoi(
        loiDangNhap?.message ||
        'Đăng nhập thất bại. Vui lòng thử lại.'
      )
    } finally {
      setDangGui(false)
    }
  }

  return (
    <div className="login-bg">
      {/* Khối đăng nhập */}
      <div className="login-card">

        {/* Logo */}
        <div className="login-brand">
          <span className="login-brand-icon">
            <HeartPulse size={28} />
          </span>

          <div>
            <strong>MediCare</strong>
            <small>Medical Administration</small>
          </div>
        </div>

        {/* Tiêu đề */}
        <div className="login-intro">
          <span className="eyebrow">
            CỔNG QUẢN TRỊ HỆ THỐNG
          </span>

          <h1>Chào mừng trở lại</h1>

          <p>
            Đăng nhập để quản lý hệ thống
            hồ sơ y tế an toàn.
          </p>
        </div>

        {/* Form đăng nhập */}
        <form onSubmit={xuLyDangNhap}>

          {/* Email */}
          <label className="login-label">
            Email Admin

            <div className="input-icon">
              <Mail size={18} />

              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(suKien) =>
                  setEmail(suKien.target.value)
                }
                required
                placeholder="admin@example.com"
                disabled={dangGui}
              />
            </div>
          </label>

          {/* Mật khẩu */}
          <label className="login-label">
            Mật khẩu

            <div className="input-icon">
              <LockKeyhole size={18} />

              <input
                type={
                  hienMatKhau
                    ? 'text'
                    : 'password'
                }
                autoComplete="current-password"
                value={matKhau}
                onChange={(suKien) =>
                  setMatKhau(suKien.target.value)
                }
                required
                placeholder="Nhập mật khẩu"
                disabled={dangGui}
              />

              <button
                type="button"
                className="password-eye"
                aria-label={
                  hienMatKhau
                    ? 'Ẩn mật khẩu'
                    : 'Hiện mật khẩu'
                }
                onClick={() =>
                  setHienMatKhau(
                    (giaTri) => !giaTri
                  )
                }
              >
                {hienMatKhau ? (
                  <EyeOff size={17} />
                ) : (
                  <Eye size={17} />
                )}
              </button>
            </div>
          </label>

          {/* Hiển thị lỗi đăng nhập */}
          {(loi || thongBaoPhien) && (
            <div
              className="login-error"
              role="alert"
            >
              {loi || thongBaoPhien}
            </div>
          )}

          {/* Nút đăng nhập */}
          <button
            type="submit"
            className="btn btn-primary login-submit"
            disabled={dangGui}
          >
            {dangGui
              ? 'Đang xác thực...'
              : 'Đăng nhập Admin'}
          </button>
        </form>

        {/* Thông báo bảo mật */}
        <div className="login-footer">
          <ShieldCheck size={16} />
          Truy cập dành riêng cho quản trị viên
        </div>
      </div>

      {/* Khối giới thiệu bên phải */}
      <div className="login-deco">
        <div className="login-deco-content">
          <HeartPulse size={60} />

          <h2>Quản trị y tế thông minh</h2>

          <p>
            Quản lý tài khoản, bác sĩ và nhật ký
            hoạt động trên một nền tảng thống nhất.
          </p>

          <div className="deco-line">
            <Activity size={18} />
            An toàn • Minh bạch • Thuận tiện
          </div>
        </div>
      </div>
    </div>
  )
}

// =====================================================
// 3. BỐ CỤC CHÍNH CỦA TRANG ADMIN
// =====================================================

function BoCuc({ onDangXuat, phien }) {
  const [moMenu, setMoMenu] = useState(false)
  const [dangDangXuat, setDangDangXuat] = useState(false)
  const [loiDangXuat, setLoiDangXuat] = useState('')
  const [moXacNhanDangXuat, setMoXacNhanDangXuat] = useState(false)
  const khoaDangXuat = useRef(false)
  const nutHuyDangXuat = useRef(null)

  const viTri = useLocation()

  // Đồng bộ menu với bàn phím và khóa cuộn nền khi mở trên thiết bị nhỏ.
  useEffect(() => {
    if (!moMenu) return
    const cuonCu = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const dongBangPhim = suKien => {
      if (suKien.key === 'Escape' && !document.querySelector('dialog[open]')) setMoMenu(false)
    }
    window.addEventListener('keydown', dongBangPhim)
    return () => {
      document.body.style.overflow = cuonCu
      window.removeEventListener('keydown', dongBangPhim)
    }
  }, [moMenu])

  // Xác định trang đang được mở.
  const trangHienTai =
    menu.find(
      (muc) => muc.path === viTri.pathname
    )?.name || 'Không tìm thấy trang'

  // Chỉ mở hộp thoại; Hủy/Escape không gọi API và không đổi trang/bộ lọc.
  const yeuCauDangXuat = () => {
    if (khoaDangXuat.current) return
    setLoiDangXuat('')
    setMoXacNhanDangXuat(true)
  }

  // Chỉ gửi yêu cầu sau khi người dùng đồng ý, khóa ngay để chặn nhấn lặp.
  const xuLyDangXuat = async () => {
    if (khoaDangXuat.current) return

    khoaDangXuat.current = true
    setDangDangXuat(true)
    setLoiDangXuat('')

    try {
      await dangXuat()

      // Cập nhật trạng thái về trang đăng nhập.
      onDangXuat()
    } catch (loiDangXuat) {
      setLoiDangXuat(loiDangXuat?.message || 'Không thể đăng xuất. Vui lòng thử lại.')
    } finally {
      khoaDangXuat.current = false
      setDangDangXuat(false)
    }
  }

  return (
    <div className="app-shell">
      {moXacNhanDangXuat && <HopThoai tieuDe="Xác nhận đăng xuất"
        dong={() => setMoXacNhanDangXuat(false)} dangGui={dangDangXuat}
        maMoTa="mo-ta-dang-xuat" lopBoSung="logout-confirm" tieuDiemBanDau={nutHuyDangXuat}>
        <span className="logout-confirm-icon" aria-hidden="true"><LogOut size={25}/></span>
        <p id="mo-ta-dang-xuat">Bạn có chắc chắn muốn đăng xuất khỏi hệ thống quản trị MediCare không?</p>
        {loiDangXuat && <div className="login-error logout-error" role="alert">{loiDangXuat}</div>}
        <div className="modal-actions">
          <button ref={nutHuyDangXuat} type="button" className="btn btn-outline" disabled={dangDangXuat} onClick={() => setMoXacNhanDangXuat(false)}>Hủy</button>
          <button type="button" className="btn btn-primary" disabled={dangDangXuat} onClick={xuLyDangXuat}>
            {dangDangXuat && <span className="spinner" aria-hidden="true"/>}
            {dangDangXuat ? 'Đang đăng xuất...' : 'Đồng ý'}
          </button>
        </div>
      </HopThoai>}

      {/* ================= SIDEBAR ================= */}
      <aside
        id="menu-quan-tri"
        aria-label="Menu quản trị"
        className={`sidebar ${
          moMenu ? 'sidebar-open' : ''
        }`}
      >
        {/* Logo hệ thống */}
        <div className="brand">
          <span className="brand-logo">
            <HeartPulse size={26} />
          </span>

          <div>
            <strong>MediCare</strong>
            <small>MEDICAL ADMIN</small>
          </div>

          <button
            type="button"
            className="close-sidebar"
            aria-label="Đóng menu"
            onClick={() => setMoMenu(false)}
          >
            <X size={20} />
          </button>
        </div>

        {/* Danh mục */}
        <div className="nav-caption">
          DANH MỤC QUẢN LÝ
        </div>

        <nav className="side-nav">
          {menu.map((muc) => {
            const BieuTuong = muc.icon

            return (
              <NavLink
                key={muc.path}
                to={muc.path}
                onClick={() => setMoMenu(false)}
                end={muc.end}
                className={({ isActive }) =>
                  isActive ? 'active' : ''
                }
              >
                <BieuTuong size={19} />
                {muc.name}
              </NavLink>
            )
          })}
        </nav>

        {/* Sidebar phía dưới */}
        <div className="sidebar-bottom">
          <div className="sidebar-security">
            <ShieldCheck size={20} />

            <div>
              <strong>Quản lý an toàn</strong>
              <span>
                Hệ thống hồ sơ y tế
              </span>
            </div>
          </div>

          <button
            type="button"
            className="logout-btn"
            onClick={yeuCauDangXuat}
            disabled={dangDangXuat}
          >
            <LogOut size={18} />

            {dangDangXuat
              ? 'Đang đăng xuất...'
              : 'Đăng xuất'}
          </button>
        </div>
      </aside>

      {/* Overlay cho giao diện mobile */}
      {moMenu && (
        <div
          className="sidebar-overlay"
          onClick={() => setMoMenu(false)}
        />
      )}

      {/* ================= MAIN ================= */}
      <div className="main-area">

        {/* Header */}
        <header className="topbar">
          <div className="topbar-left">

            {/* Nút mở menu mobile */}
            <button
              type="button"
              className="menu-toggle"
              aria-label="Mở menu"
              aria-controls="menu-quan-tri"
              aria-expanded={moMenu}
              onClick={() => setMoMenu(true)}
            >
              <Menu size={22} />
            </button>

            {/* Breadcrumb */}
            <div className="breadcrumb-top">
              Quản trị
              <span>/</span>
              <b>{trangHienTai}</b>
            </div>
          </div>

          <div className="topbar-right">

            {/* Trạng thái phiên đăng nhập */}
            <span className="connection-status">
              <span className="status-dot" />
              Bảo vệ bởi phiên đăng nhập
            </span>

            {/* Thông tin Admin */}
            <Link to="/profile" className="admin-profile" aria-label="Xem hồ sơ Admin" onClick={() => setMoMenu(false)}>
              <span className="admin-avatar">
                A
              </span>

              <div>
                <strong>
                  {phien?.tenDangNhap || 'Quản trị viên'}
                </strong>

                <small>
                  ROLE_ADMIN
                </small>
              </div>
            </Link>
          </div>
        </header>

        {/* Nội dung các trang */}
        <main className="main-content">
          <Routes>
            <Route
              path="/"
              element={<Dashboard />}
            />

            <Route
              path="/users"
              element={<UsersPage />}
            />

            <Route
              path="/doctors"
              element={<DoctorsPage />}
            />

            <Route
              path="/audit-logs"
              element={<AuditLogs />}
            />

            <Route
              path="*"
              element={<NotFound />}
            />
            <Route path="/transactions" element={<Transactions />}/>
            <Route path="/profile" element={<AdminProfile />}/>
            <Route path="/system-status" element={<SystemStatus />}/>
          </Routes>
        </main>

        {/* Footer */}
        <footer className="app-footer">
          © 2026 MediCare ·
          Hệ thống quản lý hồ sơ y tế trên Blockchain ·
          Admin Portal
        </footer>
      </div>
    </div>
  )
}

// =====================================================
// 4. QUẢN LÝ PHIÊN ĐĂNG NHẬP ADMIN
// =====================================================

function UngDung() {
  // loading: đang kiểm tra phiên
  // admin: đã đăng nhập hợp lệ
  // guest: chưa đăng nhập hoặc không có quyền
  const [trangThai, setTrangThai] = useState('loading')
  const [phien, setPhien] = useState(null)
  const [thongBaoPhien, setThongBaoPhien] = useState('')

  useEffect(() => {
    const hetPhien = () => {
      setTrangThai('guest')
      setPhien(null)
      setThongBaoPhien('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    }
    window.addEventListener('admin:het-phien', hetPhien)
    return () => window.removeEventListener('admin:het-phien', hetPhien)
  }, [])

  useEffect(() => {
    let conHieuLuc = true

    async function kiemTraPhienAdmin() {
      try {
        const duLieu = await apiAdmin.me()

        // Kiểm tra đúng cấu trúc Backend:
        // daXacThuc = true
        // vaiTro = ADMIN
        // danhSachQuyen chứa ROLE_ADMIN
        if (conHieuLuc) {
          setPhien(duLieu)
          setTrangThai(
            coQuyenAdmin(duLieu)
              ? 'admin'
              : 'guest'
          )
        }
      } catch (loi) {
        if (conHieuLuc) {
          setPhien(null)
          if (![401, 403].includes(loi.status)) setThongBaoPhien(loi.message)
          setTrangThai('guest')
        }
      }
    }

    kiemTraPhienAdmin()

    // Tránh cập nhật state sau khi component unmount.
    return () => {
      conHieuLuc = false
    }
  }, [])

  // Đang kiểm tra phiên đăng nhập.
  if (trangThai === 'loading') {
    return (
      <div className="splash">
        <HeartPulse size={34} />
        <div>
          Đang kiểm tra phiên quản trị...
        </div>
      </div>
    )
  }

  // Chưa đăng nhập hoặc không đủ quyền.
  if (trangThai !== 'admin') {
    return (
      <DangNhap
        thongBaoPhien={thongBaoPhien}
        onDangNhap={duLieu => { setPhien(duLieu); setThongBaoPhien(''); setTrangThai('admin') }}
      />
    )
  }

  // Đã đăng nhập với quyền Admin.
  return (
    <BoCuc
      phien={phien}
      onDangXuat={() => {
        setPhien(null)
        setThongBaoPhien('')
        setTrangThai('guest')
      }}
    />
  )
}

// =====================================================
// 5. ROOT COMPONENT
// =====================================================

export default function App() {
  return (
    <BrowserRouter>
      <UngDung />
    </BrowserRouter>
  )
}
