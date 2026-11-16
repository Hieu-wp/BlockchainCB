import { useDuLieu } from '../hooks/useDuLieu'
import { Users, Stethoscope, UserRound, ShieldCheck, ArrowRight, Activity, ClipboardCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiAdmin } from '../services/api'
import { TieuDe, Hop, Rong, DangTai, ThongBaoLoi, LamMoi } from '../components/Shared'

export default function Dashboard() {
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(apiAdmin.dashboard, {})
  const cards = [
    ['Tổng người dùng', duLieu?.tongNguoiDung, Users, 'blue'],
    ['Bệnh nhân', duLieu?.tongBenhNhan, UserRound, 'teal'],
    ['Tài khoản bác sĩ', duLieu?.tongTaiKhoanVaiTroDoctor, Stethoscope, 'purple'],
    ['Hồ sơ bác sĩ', duLieu?.tongHoSoBacSi, Stethoscope, 'blue'],
    ['Tài khoản đang hoạt động', duLieu?.taiKhoanDangHoatDong, ShieldCheck, 'teal'],
    ['Bác sĩ chờ duyệt', duLieu?.bacSiChoDuyet, ClipboardCheck, 'amber'],
  ]
  return <><TieuDe nhan="TỔNG QUAN HỆ THỐNG" tieuDe="Tổng quan quản trị" moTa="Theo dõi hoạt động người dùng và bác sĩ trong hệ thống BlockchainCB."><LamMoi onClick={taiLai}/></TieuDe>
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}{dangTai ? <DangTai/> : !loi && !duLieu ? <Rong noiDung="Chưa có số liệu thống kê."/> : !loi && duLieu && <>
      <div className="stat-grid">{cards.map(([nhan, so, Icon, mau]) => <div className="stat-card" key={nhan}><div className={`stat-icon ${mau}`}><Icon size={24}/></div><div className="stat-content"><div>{nhan}</div><strong>{so ?? '—'}</strong><small>Số liệu từ hệ thống</small></div></div>)}</div>
      <div className="two-col"><Hop tieuDe="Tình trạng hệ thống" phuDe="Dữ liệu tại lần tải gần nhất"><div className="metric-row"><span><ShieldCheck size={18}/> Tài khoản đang hoạt động</span><b>{duLieu.taiKhoanDangHoatDong ?? '—'}</b></div><div className="metric-row"><span><Stethoscope size={18}/> Hồ sơ bác sĩ</span><b>{duLieu.tongHoSoBacSi ?? '—'}</b></div><div className="metric-row"><span><Activity size={18}/> Kết nối dữ liệu</span><span className="pill success">Đã kết nối</span></div></Hop>
      <Hop tieuDe="Truy cập nhanh" phuDe="Các chức năng quản trị"><div className="quick-links"><Link to="/users"><span><Users size={18}/> Quản lý người dùng</span><ArrowRight size={17}/></Link><Link to="/doctors"><span><Stethoscope size={18}/> Quản lý bác sĩ</span><ArrowRight size={17}/></Link><Link to="/audit-logs"><span><ShieldCheck size={18}/> Nhật ký quản trị</span><ArrowRight size={17}/></Link></div></Hop></div>
    </>}</>
}
