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
      'Không thể kết nối đến Hệ thống.'
    )
  }

  if (phanHoi.status >= 500) {
    throw loiHttp(phanHoi.status, `Máy chủ chưa sẵn sàng hoặc gặp lỗi (HTTP ${phanHoi.status}). Kiểm tra Spring Boot tại localhost:8080.`)
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
      'Không kết nối được máy chủ để đăng nhập.'
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
// Giữ một yêu cầu đang chạy cho mọi caller, không tự lặp lại POST khi lỗi.
let yeuCauDangXuatHienTai = null

export function dangXuat() {
  if (!yeuCauDangXuatHienTai) {
    yeuCauDangXuatHienTai = thucHienDangXuat().finally(() => {
      yeuCauDangXuatHienTai = null
    })
  }
  return yeuCauDangXuatHienTai
}

async function thucHienDangXuat() {
  const token = await layCsrfToken()
  let phanHoi
  try {
    phanHoi = await ketNoi('/logout', {
      method: 'POST',
      credentials: 'same-origin',
      // Không để lỗi tải trang login sau redirect che kết quả POST đã gửi.
      redirect: 'manual',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ _csrf: token }),
    })
  } catch {
    throw new Error('Không nhận được phản hồi đăng xuất do lỗi mạng hoặc hệ thống mất kết nối. Chưa xác nhận phiên đã được hủy. Vui lòng kiểm tra kết nối rồi thử lại.')
  }

  // Browser che redirect bằng opaqueredirect; đây chỉ là tín hiệu để kiểm tra phiên tiếp.
  const laRedirect = phanHoi.type === 'opaqueredirect' || [301, 302, 303, 307, 308].includes(phanHoi.status)
  // Với 401, kiểm tra thêm /me: phiên có thể đã hết trước thao tác này.
  if (!laRedirect && !phanHoi.ok && phanHoi.status !== 401) {
    if (phanHoi.status === 403) {
      throw loiHttp(403, 'Đăng xuất bị từ chối: CSRF không hợp lệ hoặc phiên đã thay đổi. Bạn có thể thử lại để lấy CSRF mới; hệ thống chưa xác nhận đăng xuất.')
    }
    throw loiHttp(phanHoi.status, `Hệ thống không hoàn tất yêu cầu đăng xuất (HTTP ${phanHoi.status}). Vui lòng thử lại.`)
  }

  // Không dùng yeuCau(): nó phát sự kiện hết phiên, có thể đóng UI trước khi xác minh xong.
  let kiemTra
  try {
    kiemTra = await ketNoi('/api/admin/me', {
      method: 'GET', credentials: 'same-origin', cache: 'no-store', redirect: 'follow',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new Error('Đã gửi yêu cầu đăng xuất nhưng không xác minh được phiên do lỗi mạng hoặc Hệ thống mất kết nối. Chưa thể xác nhận đăng xuất. Vui lòng thử lại.')
  }

  if ([401, 403].includes(kiemTra.status)) return true
  if (!kiemTra.ok) {
    throw loiHttp(kiemTra.status, `Đã gửi yêu cầu đăng xuất nhưng hệ thống chưa xác minh được phiên (HTTP ${kiemTra.status}). Vui lòng thử lại.`)
  }

  const diaChi = new URL(kiemTra.url, window.location.origin)
  const kieuNoiDung = kiemTra.headers.get('content-type') || ''
  if (diaChi.origin !== window.location.origin) {
    throw new Error('Không xác minh được phiên qua cùng nguồn Frontend. Kiểm tra cấu hình redirect của Vite proxy.')
  }
  if (diaChi.pathname === '/login' && kieuNoiDung.includes('text/html')) {
    const taiLieu = new DOMParser().parseFromString(await kiemTra.text(), 'text/html')
    if (taiLieu.querySelector('input[name="_csrf"]')?.value) return true
  }
  if (kieuNoiDung.includes('application/json')) {
    let phien
    try { phien = await kiemTra.json() } catch {
      throw new Error('Phản hồi kiểm tra phiên không hợp lệ. Chưa thể xác nhận đăng xuất; vui lòng thử lại.')
    }
    if (phien?.daXacThuc === false) return true
    if (coQuyenAdmin(phien)) {
      throw new Error('Phiên Admin vẫn còn hiệu lực. Chưa thể xác nhận đăng xuất; vui lòng thử lại.')
    }
  }
  throw new Error('Phản hồi kiểm tra phiên không xác nhận được trạng thái đăng xuất. Vui lòng thử lại.')
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

  tuChoiBacSi: (ma, lyDo) =>
    yeuCau(`/api/admin/doctors/${encodeURIComponent(ma)}/reject`, {
      method: 'POST', data: { lyDo },
    }),

  // Danh sách bác sĩ chờ phê duyệt
  bacSiChoDuyet: () =>
    yeuCau('/api/admin/doctors/pending'),

  // Nhật ký quản trị
  nhatKy: (params = {}) =>
    yeuCau(
      `/api/admin/audit-logs?${new URLSearchParams(params)}`
    ),
}
