import { Activity, RefreshCw, ShieldCheck, Server, CircleHelp } from 'lucide-react'
import { kiemTraHeThong } from '../services/trangThaiHeThong'
import { useDuLieu } from '../hooks/useDuLieu'
import { dinhDangNgay } from '../utils/dinhDang'
import { TieuDe, Hop, DangTai, ThongBaoLoi } from '../components/Shared'

const nhanKetQua = { khaDung: ['Khả dụng', 'success'], khongHopLe: ['Phản hồi không hợp lệ', 'warning'], loi: ['Kiểm tra thất bại', 'danger'] }

export default function SystemStatus() {
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(kiemTraHeThong)
  const danhSach = duLieu?.ketQua || []
  const phien = danhSach.find(muc => muc.duongDan === '/api/admin/me')
  const coPhanHoi = danhSach.some(muc => muc.coPhanHoi)
  const coLoi = danhSach.some(muc => muc.trangThai !== 'khaDung')
  const coKhaDung = danhSach.some(muc => muc.trangThai === 'khaDung')
  return <>
    <TieuDe nhan="KIỂM TRA KHẢ DỤNG" tieuDe="Trạng thái hệ thống" moTa="Kết quả các yêu cầu đọc từ trình duyệt tại lần kiểm tra gần nhất.">
      <button type="button" className="btn btn-outline" onClick={taiLai} disabled={dangTai}><RefreshCw size={16}/>{dangTai ? 'Đang kiểm tra...' : 'Kiểm tra lại'}</button>
    </TieuDe>
    <div className="info-banner"><CircleHelp size={20}/><div><strong>Chưa có API giám sát hệ thống chuyên dụng.</strong><span>Kiểm tra Admin API chỉ xác minh phản hồi HTTP và cấu trúc dữ liệu; không chứng minh Supabase, Blockchain RPC hoặc Smart Contract đang hoạt động.</span></div></div>
    {dangTai ? <DangTai/> : loi ? <ThongBaoLoi loi={loi} onRetry={taiLai}/> : duLieu && <>
      <div className="status-summary-grid">
        <Hop tieuDe="Kết nối Backend"><div className="status-summary"><Server size={24}/><span className={`pill ${coKhaDung && !coLoi ? 'success' : 'warning'}`}>{coKhaDung ? (coLoi ? 'Kết nối một phần, có API lỗi' : 'Các API kiểm tra có phản hồi') : coPhanHoi ? 'Chưa xác minh được Backend' : 'Chưa kết nối được'}</span></div><p className="field-help">Phản hồi HTTP lỗi có thể đến từ Backend hoặc proxy.</p></Hop>
        <Hop tieuDe="Phiên đăng nhập"><div className="status-summary"><ShieldCheck size={24}/><span className={`pill ${phien?.trangThai === 'khaDung' ? 'success' : 'warning'}`}>{phien?.trangThai === 'khaDung' ? 'Đã xác minh quyền Admin' : 'Chưa xác minh được phiên'}</span></div></Hop>
      </div>
      <section className="panel" aria-label="Kết quả kiểm tra endpoint">
        <div className="panel-head"><h2><Activity size={18}/> Endpoint đã kiểm tra</h2><span className="muted small">Kiểm tra gần nhất: <time dateTime={duLieu.thoiDiem}>{dinhDangNgay(duLieu.thoiDiem)}</time></span></div>
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Bảng trạng thái endpoint, cuộn ngang để xem đầy đủ"><table className="medical-table"><thead><tr><th scope="col">Thành phần</th><th scope="col">Yêu cầu</th><th scope="col">Kết quả</th><th scope="col">Thông tin</th></tr></thead><tbody>
          {danhSach.map(muc => <tr key={muc.duongDan}><td>{muc.ten}</td><td><code className="endpoint-text">GET {muc.duongDan}</code></td><td><span className={`pill ${nhanKetQua[muc.trangThai][1]}`}>{nhanKetQua[muc.trangThai][0]}</span></td><td className="description-col">{muc.trangThai === 'khaDung' ? 'Đã nhận phản hồi đúng cấu trúc cần thiết.' : muc.trangThai === 'khongHopLe' ? 'Đã nhận phản hồi nhưng chưa xác minh được dữ liệu mong đợi.' : <>{muc.maHttp && <b>HTTP {muc.maHttp}: </b>}{muc.thongBao}</>}</td></tr>)}
        </tbody></table></div>
      </section>
    </>}
    <Hop tieuDe="Thành phần chưa thể xác minh" phuDe="Cần API giám sát riêng"><div className="unverified-grid">
      {['Supabase / PostgreSQL', 'Blockchain RPC', 'Smart Contract'].map(ten => <div key={ten}><strong>{ten}</strong><span className="pill neutral">Chưa thể xác minh</span></div>)}
    </div></Hop>
  </>
}
