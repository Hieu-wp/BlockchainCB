import { apiAdmin, coQuyenAdmin } from './api.js'

// Chỉ kiểm tra GET đã tồn tại trong Controller. Không suy diễn sức khỏe hạ tầng từ HTTP 2xx.
const danhSachPhepKiemTra = [
  { ten: 'Admin API', duongDan: '/api/admin/health', goi: apiAdmin.sucKhoe, hopLe: duLieu => duLieu?.trangThai === 'thanhCong' },
  { ten: 'Phiên Admin', duongDan: '/api/admin/me', goi: apiAdmin.me, hopLe: coQuyenAdmin },
  { ten: 'Dashboard', duongDan: '/api/admin/dashboard', goi: apiAdmin.dashboard, hopLe: duLieu => typeof duLieu?.tongNguoiDung === 'number' },
  { ten: 'Danh sách người dùng', duongDan: '/api/admin/users?trang=0&kichThuoc=1', goi: () => apiAdmin.nguoiDung({ trang: 0, kichThuoc: 1 }), hopLe: duLieu => Array.isArray(duLieu?.danhSachNguoiDung) },
  { ten: 'Tra cứu bác sĩ', duongDan: '/api/admin/doctors/search?trang=0&kichThuoc=1', goi: () => apiAdmin.bacSi({ trang: 0, kichThuoc: 1 }), hopLe: duLieu => Array.isArray(duLieu?.content) },
  { ten: 'Nhật ký quản trị', duongDan: '/api/admin/audit-logs?trang=0&kichThuoc=1', goi: () => apiAdmin.nhatKy({ trang: 0, kichThuoc: 1 }), hopLe: duLieu => Array.isArray(duLieu?.danhSachNhatKy) },
]

export async function kiemTraHeThong() {
  const ketQua = await Promise.all(danhSachPhepKiemTra.map(async phep => {
    try {
      const duLieu = await phep.goi()
      return { ten: phep.ten, duongDan: phep.duongDan, trangThai: phep.hopLe(duLieu) ? 'khaDung' : 'khongHopLe', coPhanHoi: true }
    } catch (loi) {
      return { ten: phep.ten, duongDan: phep.duongDan, trangThai: 'loi', maHttp: loi.status, coPhanHoi: Number.isInteger(loi.status), thongBao: loi.message }
    }
  }))
  return { ketQua, thoiDiem: new Date().toISOString() }
}
