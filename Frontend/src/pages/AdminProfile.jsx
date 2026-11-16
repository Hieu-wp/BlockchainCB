import { ShieldCheck, UserRound, KeyRound } from 'lucide-react'
import { apiAdmin, coQuyenAdmin } from '../services/api'
import { useDuLieu } from '../hooks/useDuLieu'
import { TieuDe, Hop, DangTai, ThongBaoLoi, Rong, LamMoi } from '../components/Shared'

export default function AdminProfile() {
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(apiAdmin.me)
  const loiPhien = loi || (duLieu && !coQuyenAdmin(duLieu) ? new Error('Thông tin phiên không xác nhận đủ quyền ADMIN. Vui lòng đăng nhập lại.') : null)
  return <>
    <TieuDe nhan="TÀI KHOẢN QUẢN TRỊ" tieuDe="Hồ sơ Admin" moTa="Thông tin phiên đăng nhập được cung cấp bởi hệ thống."><LamMoi onClick={taiLai}/></TieuDe>
    {dangTai ? <DangTai/> : loiPhien ? <ThongBaoLoi loi={loiPhien} onRetry={taiLai}/> : !duLieu ? <Rong noiDung="Chưa có thông tin phiên đăng nhập."/> : <>
      <section className="profile-summary" aria-label="Tóm tắt tài khoản Admin">
        <span className="profile-symbol"><UserRound size={32}/></span><div><h2>{duLieu.tenDangNhap || 'Chưa có tên đăng nhập'}</h2><p>Tài khoản quản trị MediCare</p></div>
        <span className="pill success"><ShieldCheck size={14}/> Phiên Admin hợp lệ</span>
      </section>
      <div className="two-col">
        <Hop tieuDe="Thông tin phiên"><dl className="detail-grid">
          <div><dt>Tên đăng nhập</dt><dd>{duLieu.tenDangNhap || 'Chưa được cung cấp'}</dd></div>
          <div><dt>Vai trò</dt><dd><span className="role-tag">{duLieu.vaiTro}</span></dd></div>
          <div><dt>Trạng thái xác thực</dt><dd>Đã xác thực</dd></div>
          <div><dt>Trạng thái phiên đăng nhập</dt><dd>Hợp lệ tại lần kiểm tra gần nhất</dd></div>
        </dl></Hop>
        <Hop tieuDe="Danh sách quyền" phuDe="Quyền do Backend trả về">
          <ul className="permission-list">{duLieu.danhSachQuyen.map((quyen, chiSo) => <li key={`${quyen}-${chiSo}`}><KeyRound size={16}/><code>{quyen}</code></li>)}</ul>
          <p className="field-help">Các quyền được hiển thị theo phiên hiện tại.</p>
        </Hop>
      </div>
    </>}
    <div className="info-banner"><ShieldCheck size={20}/><div><strong>Thông tin chỉ đọc</strong><span>Hiện chưa hỗ trợ sửa hồ sơ hoặc đổi mật khẩu trên cổng quản trị.</span></div></div>
  </>
}
