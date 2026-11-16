import { useEffect, useState } from 'react'
import { Users, Stethoscope, UserRound, ShieldCheck, ArrowRight, Activity, ClipboardCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiAdmin } from '../services/api'
import { TieuDe, Hop, DangTai, ThongBaoLoi, LamMoi } from '../components/Shared'

export default function Dashboard() {
  const [duLieu, setDuLieu] = useState(null)
  const [loi, setLoi] = useState(null)
  const [dangTai, setDangTai] = useState(true)
  const [lan, setLan] = useState(0)
  useEffect(() => {
    let conHieuLuc = true
    apiAdmin.dashboard().then(data => { if(conHieuLuc) setDuLieu(data) }).catch(err => { if(conHieuLuc) setLoi(err) }).finally(() => { if(conHieuLuc) setDangTai(false) })
    return () => { conHieuLuc = false }
  }, [lan])
  const taiLai = () => { setDangTai(true); setLoi(null); setLan(x => x + 1) }
  const cards = [
    ['Tổng người dùng', duLieu?.tongNguoiDung, Users, 'blue'],
    ['Bệnh nhân', duLieu?.tongBenhNhan, UserRound, 'teal'],
    ['Tài khoản bác sĩ', duLieu?.tongTaiKhoanVaiTroDoctor, Stethoscope, 'purple'],
    ['Bác sĩ chờ duyệt', duLieu?.bacSiChoDuyet, ClipboardCheck, 'amber'],
  ]
  return <><TieuDe nhan="TỔNG QUAN HỆ THỐNG" tieuDe="Tổng quan quản trị" moTa="Theo dõi hoạt động người dùng và bác sĩ trong hệ thống BlockchainCB."><LamMoi onClick={taiLai}/></TieuDe>
    {loi && <ThongBaoLoi loi={loi} onRetry={taiLai}/>}{dangTai ? <DangTai/> : duLieu && <>
      <div className="stat-grid">{cards.map(([nhan, so, Icon, mau]) => <div className="stat-card" key={nhan}><div className={`stat-icon ${mau}`}><Icon size={24}/></div><div className="stat-content"><div>{nhan}</div><strong>{so ?? '—'}</strong><small>Số liệu từ hệ thống</small></div></div>)}</div>
      <div className="two-col"><Hop tieuDe="Tình trạng hệ thống" phuDe="Dữ liệu thời gian thực từ Backend"><div className="metric-row"><span><ShieldCheck size={18}/> Tài khoản đang hoạt động</span><b>{duLieu.taiKhoanDangHoatDong ?? '—'}</b></div><div className="metric-row"><span><Stethoscope size={18}/> Hồ sơ bác sĩ</span><b>{duLieu.tongHoSoBacSi ?? '—'}</b></div><div className="metric-row"><span><Activity size={18}/> Kết nối dữ liệu</span><span className="pill success">Đã kết nối</span></div></Hop>
      <Hop tieuDe="Truy cập nhanh" phuDe="Các chức năng quản trị"><div className="quick-links"><Link to="/users"><span><Users size={18}/> Quản lý người dùng</span><ArrowRight size={17}/></Link><Link to="/doctors"><span><Stethoscope size={18}/> Quản lý bác sĩ</span><ArrowRight size={17}/></Link><Link to="/audit-logs"><span><ShieldCheck size={18}/> Nhật ký quản trị</span><ArrowRight size={17}/></Link></div></Hop></div>
    </>}</>
}
