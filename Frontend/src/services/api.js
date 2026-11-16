
/**
 * BlockchainCB - API quản trị viên
 *
 * Sử dụng Spring Security Form Login + Session Cookie HttpOnly.
 * Không lưu mật khẩu, session ID hoặc CSRF token vào localStorage.
 */

// Tạo lỗi HTTP để các trang React có thể xử lý.
const loiHttp = (ma, thongBao) =>
  Object.assign(new Error(thongBao), { status: ma })

/**
 * Kiểm tra dữ liệu phiên Admin do Backend trả về.
 *
 * Backend:
 * {
 *   tenDangNhap: "admin@gmail.com",
 *   vaiTro: "ADMIN",
 *   danhSachQuyen: ["ROLE_ADMIN", "FACTOR_PASSWORD"],
 *   daXacThuc: true
 * }
 */
export function coQuyenAdmin(duLieu) {
  return (
    duLieu?.daXacThuc === true &&
    duLieu?.vaiTro === 'ADMIN' &&
    Array.isArray(duLieu?.danhSachQuyen) &&
    duLieu.danhSachQuyen.includes('ROLE_ADMIN')
  )
}

/**
 * Lấy CSRF token từ trang đăng nhập Spring Security.
 */
export async function layCsrfToken() {
  let phanHoi

  try {
    phanHoi = await fetch('/login', {
      method: 'GET',
      credentials: 'same-origin',
    })
  } catch {
    throw new Error(
      'Không kết nối được Backend. Kiểm tra Spring Boot tại localhost:8080.'
    )
  }

  if (!phanHoi.ok) {
    throw loiHttp(
      phanHoi.status,
      'Không tải được trang xác thực.'
    )
  }

  const html = await phanHoi.text()

  const taiLieu = new DOMParser().parseFromString(
    html,
    'text/html'
  )

  const token = taiLieu.querySelector(
    'input[name="_csrf"]'
  )?.value

  if (!token) {
    throw new Error(
      'Không tìm thấy CSRF token. Kiểm tra cấu hình Spring Security.'
    )
  }

  return token
}

/**
 * Hàm gọi API dùng chung.
 */
export async function yeuCau(duongDan, tuyChon = {}) {
  const phuongThuc = (
    tuyChon.method || 'GET'
  ).toUpperCase()

  const headers = {
    Accept: 'application/json',
    ...tuyChon.headers,
  }

  let body

  if (tuyChon.data !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(tuyChon.data)
  }

  // Các request thay đổi dữ liệu cần CSRF token.
  if (!['GET', 'HEAD', 'OPTIONS'].includes(phuongThuc)) {
    headers['X-CSRF-TOKEN'] = await layCsrfToken()
  }

  let phanHoi

  try {
    phanHoi = await fetch(duongDan, {
      method: phuongThuc,
      headers,
      body,
      credentials: 'same-origin',
    })
  } catch {
    throw new Error(
      'Không thể kết nối đến Backend Spring Boot.'
    )
  }

  const contentType =
    phanHoi.headers.get('content-type') || ''

  const duongDanCuoi = new URL(
    phanHoi.url,
    window.location.origin
  ).pathname

  // Nếu Spring Security chuyển về trang đăng nhập,
  // phiên hiện tại không còn hợp lệ.
  if (
    contentType.includes('text/html') ||
    duongDanCuoi === '/login'
  ) {
    throw loiHttp(
      401,
      'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    )
  }

  let duLieu = null

  if (phanHoi.status !== 204) {
    duLieu = contentType.includes('application/json')
      ? await phanHoi.json()
      : await phanHoi.text()
  }

  if (!phanHoi.ok) {
    const thongBao =
      duLieu && typeof duLieu === 'object'
        ? (
            duLieu.message ||
            duLieu.detail ||
            duLieu.error
          )
        : duLieu

    throw loiHttp(
      phanHoi.status,
      thongBao &&
      String(thongBao).length < 200
        ? String(thongBao)
        : `Yêu cầu thất bại (HTTP ${phanHoi.status}).`
    )
  }

  return duLieu
}

/**
 * Đăng nhập Admin.
 *
 * Spring Security nhận:
 * username=email
 * password=matKhau
 * _csrf=token
 */
export async function dangNhap(email, matKhau) {
  const csrf = await layCsrfToken()

  const body = new URLSearchParams({
    username: email.trim(),
    password: matKhau,
    _csrf: csrf,
  })

  let phanHoi

  try {
    phanHoi = await fetch('/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body,
    })
  } catch {
    throw new Error(
      'Không kết nối được Backend để đăng nhập.'
    )
  }

  // Spring Security thường redirect về /login?error
  // khi đăng nhập thất bại.
  const diaChiSauDangNhap = new URL(
    phanHoi.url,
    window.location.origin
  )

  if (
    diaChiSauDangNhap.pathname === '/login' &&
    diaChiSauDangNhap.searchParams.has('error')
  ) {
    throw loiHttp(
      401,
      'Email hoặc mật khẩu không chính xác.'
    )
  }

  if (!phanHoi.ok) {
    throw loiHttp(
      phanHoi.status,
      `Đăng nhập thất bại (HTTP ${phanHoi.status}).`
    )
  }

  // Xác minh phiên đăng nhập bằng Backend.
  const duLieu = await yeuCau('/api/admin/me')

  if (!coQuyenAdmin(duLieu)) {
    throw loiHttp(
      403,
      'Phiên đăng nhập không có quyền ADMIN.'
    )
  }

  return duLieu
}

/**
 * Đăng xuất Admin qua Spring Security.
 */
export async function dangXuat() {
  const token = await layCsrfToken()

  const phanHoi = await fetch('/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      _csrf: token,
    }),
  })

  if (!phanHoi.ok) {
    throw loiHttp(
      phanHoi.status,
      'Không thể đăng xuất. Vui lòng thử lại.'
    )
  }

  return true
}

/**
 * Các API Backend Admin đã xây dựng.
 */
export const apiAdmin = {
  // Thông tin phiên đăng nhập Admin
  me: () =>
    yeuCau('/api/admin/me'),

  // Dashboard quản trị
  dashboard: () =>
    yeuCau('/api/admin/dashboard'),

  // Danh sách người dùng: tìm kiếm, lọc, phân trang
  nguoiDung: (params = {}) =>
    yeuCau(
      `/api/admin/users?${new URLSearchParams(params)}`
    ),

  // Chi tiết người dùng
  chiTietNguoiDung: (id) =>
    yeuCau(`/api/admin/users/${id}`),

  // Khóa tài khoản PATIENT
  khoa: (id, lyDo) =>
    yeuCau(`/api/admin/users/${id}/lock`, {
      method: 'POST',
      data: { lyDo },
    }),

  // Mở khóa tài khoản PATIENT
  moKhoa: (id) =>
    yeuCau(`/api/admin/users/${id}/unlock`, {
      method: 'POST',
    }),

  // Danh sách và tìm kiếm bác sĩ
  bacSi: (params = {}) =>
    yeuCau(
      `/api/admin/doctors/search?${new URLSearchParams(params)}`
    ),

  // Danh sách bác sĩ chờ phê duyệt
  bacSiChoDuyet: () =>
    yeuCau('/api/admin/doctors/pending'),

  // Nhật ký quản trị
  nhatKy: (params = {}) =>
    yeuCau(
      `/api/admin/audit-logs?${new URLSearchParams(params)}`
    ),
}
