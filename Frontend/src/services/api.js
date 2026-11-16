// API sử dụng session cookie HttpOnly. Không lưu mật khẩu hoặc token trong localStorage.
const loiHttp = (ma, thongBao) => Object.assign(new Error(thongBao), { status: ma })

export async function layCsrfToken() {
  const phanHoi = await fetch('/login', { credentials: 'same-origin' })
  if (!phanHoi.ok) throw loiHttp(phanHoi.status, 'Không tải được trang xác thực.')
  const html = await phanHoi.text()
  const taiLieu = new DOMParser().parseFromString(html, 'text/html')
  const token = taiLieu.querySelector('input[name="_csrf"]')?.value
  if (!token) throw new Error('Không tìm thấy CSRF token. Kiểm tra cấu hình Spring Security của Backend.')
  return token
}

export async function yeuCau(duongDan, tuyChon = {}) {
  const phuongThuc = (tuyChon.method || 'GET').toUpperCase()
  const headers = { Accept: 'application/json', ...tuyChon.headers }
  let body
  if (tuyChon.data !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(tuyChon.data)
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(phuongThuc)) {
    headers['X-CSRF-TOKEN'] = await layCsrfToken()
  }
  const phanHoi = await fetch(duongDan, { method: phuongThuc, headers, body, credentials: 'same-origin' })
  const contentType = phanHoi.headers.get('content-type') || ''
  if (contentType.includes('text/html') || phanHoi.url.endsWith('/login')) {
    throw loiHttp(401, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
  }
  const duLieu = contentType.includes('application/json') ? await phanHoi.json() : await phanHoi.text()
  if (!phanHoi.ok) {
    const thongBao = typeof duLieu === 'object'
      ? (duLieu?.message || duLieu?.detail || duLieu?.error)
      : duLieu
    throw loiHttp(phanHoi.status, thongBao && String(thongBao).length < 200 ? String(thongBao) : `Yêu cầu thất bại (HTTP ${phanHoi.status}).`)
  }
  return duLieu
}

export async function dangNhap(email, matKhau) {
  const csrf = await layCsrfToken()
  const body = new URLSearchParams({ username: email, password: matKhau, _csrf: csrf })
  const phanHoi = await fetch('/login', {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body,
  })
  // Spring formLogin thường redirect khi đăng nhập thành công/thất bại.
  if (phanHoi.url.includes('error')) throw new Error('Email hoặc mật khẩu không chính xác.')
  const duLieu = await yeuCau('/api/admin/me')
  if (!duLieu?.dangNhapThanhCong || duLieu?.vaiTro !== 'ROLE_ADMIN') {
    throw new Error('Tài khoản không có quyền ADMIN.')
  }
  return duLieu
}

export async function dangXuat() {
  const token = await layCsrfToken()
  await fetch('/logout', {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ _csrf: token }),
  })
}

export const apiAdmin = {
  me: () => yeuCau('/api/admin/me'),
  dashboard: () => yeuCau('/api/admin/dashboard'),
  nguoiDung: (params = {}) => yeuCau(`/api/admin/users?${new URLSearchParams(params)}`),
  khoa: (id, lyDo) => yeuCau(`/api/admin/users/${id}/lock`, { method: 'POST', data: { lyDo } }),
  moKhoa: id => yeuCau(`/api/admin/users/${id}/unlock`, { method: 'POST' }),
  bacSi: (params = {}) => yeuCau(`/api/admin/doctors/search?${new URLSearchParams(params)}`),
  nhatKy: (params = {}) => yeuCau(`/api/admin/audit-logs?${new URLSearchParams(params)}`),
}
