import { AlertCircle, RefreshCw, Search, ChevronLeft, ChevronRight } from 'lucide-react'

export function TieuDe({ nhan, tieuDe, moTa, children }) {
  return <div className="page-heading"><div><div className="eyebrow">{nhan || 'BẢNG ĐIỀU KHIỂN ADMIN'}</div><h1>{tieuDe}</h1><p>{moTa}</p></div><div className="heading-actions">{children}</div></div>
}
export function Hop({ tieuDe, children, phuDe }) {
  return <section className="panel"><div className="panel-head"><h2>{tieuDe}</h2>{phuDe && <span className="muted small">{phuDe}</span>}</div><div className="panel-body">{children}</div></section>
}
export function TrangThai({ giaTri }) {
  const map = { ACTIVE: ['Đang hoạt động','success'], INACTIVE: ['Đã khóa','danger'], PENDING: ['Chờ duyệt','warning'], APPROVED: ['Đã duyệt','success'], REJECTED: ['Từ chối','danger'], USER_LOCK: ['Khóa tài khoản','danger'], USER_UNLOCK: ['Mở khóa tài khoản','success'] }
  const [nhan, mau] = map[giaTri] || [giaTri || 'Chưa có', 'neutral']
  return <span className={`pill ${mau}`}>{nhan}</span>
}
export function ThongBaoLoi({ loi, onRetry }) {
  return <div className="error-box" role="alert"><AlertCircle size={19}/><div><strong>Không tải được dữ liệu</strong><p>{loi?.message || 'Đã xảy ra lỗi.'}</p></div>{onRetry && <button className="btn btn-outline" onClick={onRetry}><RefreshCw size={15}/> Thử lại</button>}</div>
}
export function DangTai() { return <div className="loading"><span className="spinner"/>Đang tải dữ liệu từ Backend...</div> }
export function Rong({ noiDung = 'Chưa có dữ liệu phù hợp.' }) { return <div className="empty"><Search size={30}/><p>{noiDung}</p></div> }
export function PhanTrang({ trang, tongTrang, setTrang, tongPhanTu }) {
  return <div className="pagination"><span> Tổng cộng <b>{tongPhanTu ?? 0}</b> bản ghi</span><div className="pagination-buttons"><button disabled={trang <= 0} onClick={() => setTrang(Math.max(0, trang - 1))} aria-label="Trang trước"><ChevronLeft size={17}/></button><span>Trang {trang + 1}/{Math.max(tongTrang || 0, 1)}</span><button disabled={trang + 1 >= tongTrang} onClick={() => setTrang(trang + 1)} aria-label="Trang tiếp"><ChevronRight size={17}/></button></div></div>
}
export function LamMoi({ onClick }) { return <button className="btn btn-outline" onClick={onClick}><RefreshCw size={16}/> Làm mới</button> }
export const dinhDangNgay = giaTri => {
  if (!giaTri) return '—'
  const ngay = new Date(giaTri)
  return Number.isNaN(ngay.getTime()) ? giaTri : ngay.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}
