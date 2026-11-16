
import { useEffect, useState } from 'react'
import {
  BrowserRouter,
  NavLink,
  Navigate,
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
} from 'lucide-react'

// Các trang quản trị
import Dashboard from './pages/Dashboard'
import UsersPage from './pages/Users'
import DoctorsPage from './pages/Doctors'
import AuditLogs from './pages/AuditLogs'

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
]

// =====================================================
// 2. GIAO DIỆN ĐĂNG NHẬP ADMIN
// =====================================================

function DangNhap({ onDangNhap }) {
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

      onDangNhap()
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
            <strong>BlockchainCB</strong>
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
          {loi && (
            <div
              className="login-error"
              role="alert"
            >
              {loi}
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
  )
}

// =====================================================
// 3. BỐ CỤC CHÍNH CỦA TRANG ADMIN
// =====================================================

function BoCuc({ onDangXuat }) {
  const [moMenu, setMoMenu] = useState(false)
  const [dangXuat, setDangXuat] = useState(false)

  const viTri = useLocation()

  // Xác định trang đang được mở.
  const trangHienTai =
    menu.find(
      (muc) => muc.path === viTri.pathname
    )?.name || 'Quản trị'

  // Xử lý đăng xuất.
  const xuLyDangXuat = async () => {
    if (dangXuat) return

    setDangXuat(true)

    try {
      await dangXuat()

      // Cập nhật trạng thái về trang đăng nhập.
      onDangXuat()
    } catch (loiDangXuat) {
      alert(
        `Không đăng xuất được: ${
          loiDangXuat?.message || 'Lỗi không xác định'
        }`
      )
    } finally {
      setDangXuat(false)
    }
  }

  // Đóng sidebar khi chuyển trang.
  useEffect(() => {
    setMoMenu(false)
  }, [viTri.pathname])

  return (
    <div className="app-shell">

      {/* ================= SIDEBAR ================= */}
      <aside
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
            <strong>BlockchainCB</strong>
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
            onClick={xuLyDangXuat}
            disabled={dangXuat}
          >
            <LogOut size={18} />

            {dangXuat
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
            <div className="admin-profile">
              <span className="admin-avatar">
                A
              </span>

              <div>
                <strong>
                  Quản trị viên
                </strong>

                <small>
                  ROLE_ADMIN
                </small>
              </div>
            </div>
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
              element={
                <Navigate
                  to="/"
                  replace
                />
              }
            />
          </Routes>
        </main>

        {/* Footer */}
        <footer className="app-footer">
          © 2026 BlockchainCB ·
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
          setTrangThai(
            coQuyenAdmin(duLieu)
              ? 'admin'
              : 'guest'
          )
        }
      } catch (loi) {
        console.error(
          'Lỗi kiểm tra phiên Admin:',
          loi
        )

        if (conHieuLuc) {
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
        onDangNhap={() =>
          setTrangThai('admin')
        }
      />
    )
  }

  // Đã đăng nhập với quyền Admin.
  return (
    <BoCuc
      onDangXuat={() =>
        setTrangThai('guest')
      }
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
