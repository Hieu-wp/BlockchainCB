import { useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Activity, LayoutDashboard, Users, Stethoscope, ScrollText, ShieldCheck, LogOut, Menu, X, HeartPulse, LockKeyhole, Mail, Eye, EyeOff } from 'lucide-react'
import Dashboard from './pages/Dashboard'
import UsersPage from './pages/Users'
import DoctorsPage from './pages/Doctors'
import AuditLogs from './pages/AuditLogs'
import { apiAdmin, dangNhap, dangXuat } from './services/api'
import './App.css'

const menu = [
  { path: '/', name: 'Tổng quan', icon: LayoutDashboard, end: true },
  { path: '/users', name: 'Người dùng', icon: Users },
  { path: '/doctors', name: 'Bác sĩ', icon: Stethoscope },
  { path: '/audit-logs', name: 'Nhật ký quản trị', icon: ScrollText },
]

function DangNhap({ onDangNhap }) {
  const [email, setEmail] = useState('')
  const [matKhau, setMatKhau] = useState('')
  const [hienMatKhau, setHienMatKhau] = useState(false)
  const [dangGui, setDangGui] = useState(false)
  const [loi, setLoi] = useState('')
  const xuLy = async e => {
    e.preventDefault(); setDangGui(true); setLoi('')
    try { await dangNhap(email.trim(), matKhau); setMatKhau(''); onDangNhap() }
    catch (err) { setLoi(err.message) } finally { setDangGui(false) }
  }
  return <div className="login-bg"><div className="login-card"><div className="login-brand"><span className="login-brand-icon"><HeartPulse size={28}/></span><div><strong>BlockchainCB</strong><small>Medical Administration</small></div></div><div className="login-intro"><span className="eyebrow">CỔNG QUẢN TRỊ HỆ THỐNG</span><h1>Chào mừng trở lại</h1><p>Đăng nhập để quản lý hệ thống hồ sơ y tế an toàn.</p></div><form onSubmit={xuLy}><label className="login-label">Email Admin<div className="input-icon"><Mail size={18}/><input type="email" autoComplete="username" value={email} onChange={e=>setEmail(e.target.value)} required placeholder="admin@example.com"/></div></label><label className="login-label">Mật khẩu<div className="input-icon"><LockKeyhole size={18}/><input type={hienMatKhau ? 'text' : 'password'} autoComplete="current-password" value={matKhau} onChange={e=>setMatKhau(e.target.value)} required placeholder="Nhập mật khẩu"/><button type="button" className="password-eye" aria-label="Hiện hoặc ẩn mật khẩu" onClick={()=>setHienMatKhau(v=>!v)}>{hienMatKhau ? <EyeOff size={17}/> : <Eye size={17}/>}</button></div></label>{loi && <div className="login-error" role="alert">{loi}</div>}<button type="submit" className="btn btn-primary login-submit" disabled={dangGui}>{dangGui ? 'Đang xác thực...' : 'Đăng nhập Admin'}</button></form><div className="login-footer"><ShieldCheck size={16}/> Truy cập dành riêng cho quản trị viên</div></div><div className="login-deco"><HeartPulse size={60}/><h2>Quản trị y tế thông minh</h2><p>Quản lý tài khoản, bác sĩ và nhật ký hoạt động trên một nền tảng thống nhất.</p><div className="deco-line"><Activity size={18}/> An toàn • Minh bạch • Thuận tiện</div></div></div>
}

function BoCuc({ onDangXuat }) {
  const [moMenu, setMoMenu] = useState(false)
  const [dangXuat, setDangXuat] = useState(false)
  const viTri = useLocation()
  const trangHienTai = menu.find(m => m.path === viTri.pathname)?.name || 'Quản trị'
  const xuLyDangXuat = async () => {
    setDangXuat(true)
    try { await dangXuat(); onDangXuat() } catch(err) { alert(`Không đăng xuất được: ${err.message}`) } finally { setDangXuat(false) }
  }
  useEffect(() => { setMoMenu(false) }, [viTri.pathname])
  return <div className="app-shell"><aside className={`sidebar ${moMenu ? 'sidebar-open' : ''}`}><div className="brand"><span className="brand-logo"><HeartPulse size={26}/></span><div><strong>BlockchainCB</strong><small>MEDICAL ADMIN</small></div><button className="close-sidebar" onClick={()=>setMoMenu(false)}><X size={20}/></button></div><div className="nav-caption">DANH MỤC QUẢN LÝ</div><nav className="side-nav">{menu.map(item => <NavLink key={item.path} to={item.path} end={item.end} className={({isActive})=>isActive ? 'active' : ''}><item.icon size={19}/>{item.name}</NavLink>)}</nav><div className="sidebar-bottom"><div className="sidebar-security"><ShieldCheck size={20}/><div><strong>Quản lý an toàn</strong><span>Hệ thống hồ sơ y tế</span></div></div><button className="logout-btn" onClick={xuLyDangXuat} disabled={dangXuat}><LogOut size={18}/> {dangXuat ? 'Đang đăng xuất...' : 'Đăng xuất'}</button></div></aside>{moMenu && <div className="sidebar-overlay" onClick={()=>setMoMenu(false)}/>}<div className="main-area"><header className="topbar"><div className="topbar-left"><button className="menu-toggle" onClick={()=>setMoMenu(true)} aria-label="Mở menu"><Menu size={22}/></button><div className="breadcrumb-top">Quản trị <span>/</span> <b>{trangHienTai}</b></div></div><div className="topbar-right"><span className="connection-status"><span className="status-dot"/> Bảo vệ bởi phiên đăng nhập</span><div className="admin-profile"><span className="admin-avatar">A</span><div><strong>Quản trị viên</strong><small>ROLE_ADMIN</small></div></div></div></header><main className="main-content"><Routes><Route path="/" element={<Dashboard/>}/><Route path="/users" element={<UsersPage/>}/><Route path="/doctors" element={<DoctorsPage/>}/><Route path="/audit-logs" element={<AuditLogs/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></main><footer className="app-footer">© 2026 BlockchainCB · Hệ thống quản lý hồ sơ y tế trên Blockchain · Admin Portal</footer></div></div>
}

function UngDung() {
  const [trangThai, setTrangThai] = useState('loading')
  const napLai = () => { setTrangThai('loading'); apiAdmin.me().then(data=> { setTrangThai(data?.dangNhapThanhCong && data?.vaiTro === 'ROLE_ADMIN' ? 'admin' : 'guest') }).catch(()=> { setTrangThai('guest') }) }
  useEffect(() => { napLai() }, [])
  if (trangThai === 'loading') return <div className="splash"><HeartPulse size={34}/><div>Đang kiểm tra phiên quản trị...</div></div>
  if (trangThai !== 'admin') return <DangNhap onDangNhap={()=>setTrangThai('admin')}/>
  return <BoCuc onDangXuat={()=>setTrangThai('guest')}/>
}
export default function App() { return <BrowserRouter><UngDung/></BrowserRouter> }
