/**
 * BlockchainCB - API quản trị viên
 *
 * Sử dụng Spring Security Form Login + Session Cookie HttpOnly.
 * Không lưu mật khẩu, session ID hoặc CSRF token vào localStorage.
 */

// Giới hạn thời gian chờ để giao diện có thể thử lại khi mạng bị treo.
async function ketNoi(duongDan, tuyChon) {
  return fetch(duongDan, { ...tuyChon, signal: AbortSignal.timeout(15000) })
}

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
    phanHoi = await ketNoi('/login', {
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
      'Không tải được trang xác thực. Kiểm tra Backend tại localhost:8080 và proxy /login.'
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
    phanHoi = await ketNoi(duongDan, {
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

  if (phanHoi.status >= 500) {
    throw loiHttp(phanHoi.status, `Backend chưa sẵn sàng hoặc gặp lỗi (HTTP ${phanHoi.status}). Kiểm tra Spring Boot tại localhost:8080.`)
  }
  if (phanHoi.status === 401) {
    window.dispatchEvent(new Event('admin:het-phien'))
    throw loiHttp(401, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
  }
  // Phân biệt hết phiên (Spring có thể trả 403) với lỗi CSRF của phiên còn hợp lệ.
  if (phanHoi.status === 403 && duongDan !== '/api/admin/me') {
    try {
      const phien = await yeuCau('/api/admin/me')
      if (!coQuyenAdmin(phien)) window.dispatchEvent(new Event('admin:het-phien'))
    } catch (loiPhien) {
      if ([401, 403].includes(loiPhien.status)) window.dispatchEvent(new Event('admin:het-phien'))
    }
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
    (phanHoi.ok && contentType.includes('text/html')) ||
    duongDanCuoi === '/login'
  ) {
    window.dispatchEvent(new Event('admin:het-phien'))
    throw loiHttp(401, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
  }

  let duLieu = null

  if (phanHoi.status !== 204) {
    duLieu = contentType.includes('application/json')
      ? await phanHoi.json()
      : contentType.includes('text/html') ? null : await phanHoi.text()
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
        : phanHoi.status === 403
          ? 'Yêu cầu bị từ chối. Kiểm tra quyền truy cập hoặc đăng nhập lại để làm mới phiên và CSRF.'
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
    phanHoi = await ketNoi('/login', {
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
  let duLieu
  try {
    duLieu = await yeuCau('/api/admin/me')
  } catch (loiPhien) {
    if (loiPhien.status === 403) throw loiHttp(403, 'Tài khoản không có quyền truy cập ADMIN.')
    throw loiPhien
  }

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

  const phanHoi = await ketNoi('/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      _csrf: token,
    }),
  })

  if (!phanHoi.ok || new URL(phanHoi.url, window.location.origin).searchParams.has('error')) {
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
  // Endpoint hiện có chỉ xác minh Admin API, không giám sát database hoặc Blockchain.
  sucKhoe: () => yeuCau('/api/admin/health'),

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
  chiTietNguoiDung: ({ ma }) =>
    yeuCau(`/api/admin/users/${encodeURIComponent(ma)}`),

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

  chiTietBacSi: ({ ma }) =>
    yeuCau(`/api/admin/doctors/${encodeURIComponent(ma)}`),

  // Danh sách bác sĩ chờ phê duyệt
  bacSiChoDuyet: () =>
    yeuCau('/api/admin/doctors/pending'),

  // Nhật ký quản trị
  nhatKy: (params = {}) =>
    yeuCau(
      `/api/admin/audit-logs?${new URLSearchParams(params)}`
    ),
}
