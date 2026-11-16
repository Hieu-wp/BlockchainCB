import test from 'node:test'
import assert from 'node:assert/strict'
import { apiAdmin, coQuyenAdmin, dangNhap, dangXuat, yeuCau } from '../src/services/api.js'

// Phản hồi được giả lập; bộ test không gửi request tới database hoặc Backend.
test('Hồi quy xác thực session và các yêu cầu quản trị', async kiemThu => {
  const banDau = { fetch: globalThis.fetch, window: globalThis.window, DOMParser: globalThis.DOMParser }
  const phienAdmin = { daXacThuc: true, vaiTro: 'ADMIN', danhSachQuyen: ['ROLE_ADMIN', 'FACTOR_PASSWORD'] }
  let hetPhien = 0
  const cuaSo = new EventTarget()
  cuaSo.location = { origin: 'http://localhost:5173' }
  cuaSo.addEventListener('admin:het-phien', () => { hetPhien += 1 })
  globalThis.window = cuaSo
  globalThis.DOMParser = class {
    parseFromString(html) {
      return { querySelector: () => html.includes('csrf-thu-nghiem') ? { value: 'csrf-thu-nghiem' } : null }
    }
  }
  const phanHoi = (url, data, status = 200, contentType = 'application/json') => {
    const ketQua = new Response(contentType.includes('json') ? JSON.stringify(data) : data, { status, headers: { 'content-type': contentType } })
    Object.defineProperty(ketQua, 'url', { value: `http://localhost:5173${url}` })
    return ketQua
  }
  const trangCsrf = () => phanHoi('/login', '<input name="_csrf" value="csrf-thu-nghiem">', 200, 'text/html')
  try {
    await kiemThu.test('Chỉ chấp nhận phiên đủ các điều kiện ADMIN', () => {
      assert.equal(coQuyenAdmin(phienAdmin), true)
      for (const duLieu of [null, {}, { ...phienAdmin, daXacThuc: false }, { ...phienAdmin, vaiTro: 'ROLE_ADMIN' }, { ...phienAdmin, danhSachQuyen: ['FACTOR_PASSWORD'] }, { ...phienAdmin, danhSachQuyen: 'ROLE_ADMIN' }]) assert.equal(coQuyenAdmin(duLieu), false)
    })
    await kiemThu.test('Đăng nhập gửi form và CSRF, xác minh bằng /me', async () => {
      const loiGoi = []
      globalThis.fetch = async (url, tuyChon) => {
        loiGoi.push(url)
        assert.equal(tuyChon.credentials, 'same-origin')
        if (url === '/login' && tuyChon.method === 'GET') return trangCsrf()
        if (url === '/login') {
          assert.equal(tuyChon.body.get('username'), 'admin@example.test')
          assert.equal(tuyChon.body.get('password'), 'mat-khau-thu')
          assert.equal(tuyChon.body.get('_csrf'), 'csrf-thu-nghiem')
          return phanHoi('/login', { dangNhapThanhCong: true, vaiTro: 'ROLE_ADMIN' })
        }
        return phanHoi(url, phienAdmin)
      }
      assert.deepEqual(await dangNhap(' admin@example.test ', 'mat-khau-thu'), phienAdmin)
      assert.deepEqual(loiGoi, ['/login', '/login', '/api/admin/me'])
    })
    await kiemThu.test('Sai mật khẩu không gọi API quản trị', async () => {
      globalThis.fetch = async (url, tuyChon) => {
        assert.equal(url, '/login')
        return tuyChon.method === 'GET' ? trangCsrf() : phanHoi('/login?error', '', 200, 'text/html')
      }
      await assert.rejects(dangNhap('a@example.test', 'sai'), /Email hoặc mật khẩu/)
    })
    await kiemThu.test('Từ chối phiên không có authority ADMIN', async () => {
      globalThis.fetch = async (url, tuyChon) => url === '/login' ? (tuyChon.method === 'GET' ? trangCsrf() : phanHoi(url, {})) : phanHoi(url, { ...phienAdmin, danhSachQuyen: ['ROLE_PATIENT'] })
      await assert.rejects(dangNhap('a@example.test', 'thu'), { status: 403 })
    })
    await kiemThu.test('Đăng nhập tài khoản không phải Admin có thông báo rõ ràng', async () => {
      globalThis.fetch = async (url, tuyChon) => url === '/login' ? (tuyChon.method === 'GET' ? trangCsrf() : phanHoi(url, {})) : phanHoi(url, { error: 'Forbidden' }, 403)
      await assert.rejects(dangNhap('a@example.test', 'thu'), /Tài khoản không có quyền truy cập ADMIN/)
    })
    await kiemThu.test('Thiếu CSRF không gửi POST', async () => {
      globalThis.fetch = async (url, tuyChon) => {
        assert.equal(tuyChon.method, 'GET')
        return phanHoi(url, '<html></html>', 200, 'text/html')
      }
      await assert.rejects(apiAdmin.khoa(5, 'thu'), /Không tìm thấy CSRF token/)
    })
    await kiemThu.test('Khóa PATIENT gửi JSON lý do và header CSRF', async () => {
      globalThis.fetch = async (url, tuyChon) => {
        if (url === '/login') return trangCsrf()
        assert.equal(url, '/api/admin/users/5/lock')
        assert.equal(tuyChon.method, 'POST')
        assert.equal(tuyChon.headers['X-CSRF-TOKEN'], 'csrf-thu-nghiem')
        assert.deepEqual(JSON.parse(tuyChon.body), { lyDo: 'Kiểm thử' })
        return phanHoi(url, { trangThai: 'INACTIVE' })
      }
      assert.deepEqual(await apiAdmin.khoa(5, 'Kiểm thử'), { trangThai: 'INACTIVE' })
    })
    await kiemThu.test('Đăng xuất gửi POST với CSRF mới', async () => {
      globalThis.fetch = async (url, tuyChon) => {
        if (url === '/login') return trangCsrf()
        assert.equal(url, '/logout')
        assert.equal(tuyChon.method, 'POST')
        assert.equal(tuyChon.body.get('_csrf'), 'csrf-thu-nghiem')
        return phanHoi('/login?logout', '', 200, 'text/html')
      }
      assert.equal(await dangXuat(), true)
    })
    await kiemThu.test('Lỗi 503 HTML không bị hiểu là hết phiên', async () => {
      const soCu = hetPhien
      globalThis.fetch = async url => phanHoi(url, '<h1>Lỗi proxy</h1>', 503, 'text/html')
      await assert.rejects(apiAdmin.dashboard(), { status: 503 })
      assert.equal(hetPhien, soCu)
    })
    await kiemThu.test('Lỗi 401 yêu cầu đăng nhập lại', async () => {
      const soCu = hetPhien
      globalThis.fetch = async url => phanHoi(url, {}, 401)
      await assert.rejects(apiAdmin.dashboard(), { status: 401 })
      assert.equal(hetPhien, soCu + 1)
    })
    await kiemThu.test('Chuyển hướng /login yêu cầu đăng nhập lại', async () => {
      globalThis.fetch = async () => phanHoi('/login', '<html></html>', 200, 'text/html')
      await assert.rejects(apiAdmin.dashboard(), { status: 401 })
    })
    await kiemThu.test('Lỗi CSRF 403 của phiên hợp lệ không tự đăng xuất, không tự gửi lại POST', async () => {
      const soCu = hetPhien
      let soPost = 0
      globalThis.fetch = async (url, tuyChon) => {
        if (url === '/login') return trangCsrf()
        if (url === '/api/admin/me') return phanHoi(url, phienAdmin)
        if (tuyChon.method === 'POST') soPost += 1
        return phanHoi(url, '<html>Forbidden</html>', 403, 'text/html')
      }
      await assert.rejects(apiAdmin.moKhoa(5), { status: 403 })
      assert.equal(hetPhien, soCu)
      assert.equal(soPost, 1)
    })
    await kiemThu.test('403 do hết phiên kích hoạt đăng nhập lại', async () => {
      const soCu = hetPhien
      globalThis.fetch = async url => phanHoi(url, {}, 403)
      await assert.rejects(apiAdmin.dashboard(), { status: 403 })
      assert.equal(hetPhien, soCu + 1)
    })
    await kiemThu.test('Mất kết nối có thông báo rõ ràng', async () => {
      globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
      await assert.rejects(apiAdmin.dashboard(), /Không thể kết nối/)
    })
    await kiemThu.test('Lọc và phân trang được mã hóa đúng', async () => {
      globalThis.fetch = async url => {
        const thamSo = new URL(url, cuaSo.location.origin).searchParams
        assert.equal(thamSo.get('tuKhoa'), 'Tên & email')
        assert.equal(thamSo.get('trang'), '2')
        return phanHoi(url, {})
      }
      await apiAdmin.nguoiDung({ tuKhoa: 'Tên & email', trang: 2 })
    })
    await kiemThu.test('Response 204 không gây lỗi parse JSON', async () => {
      globalThis.fetch = async () => new Response(null, { status: 204 })
      assert.equal(await yeuCau('/api/admin/dashboard'), null)
    })
  } finally {
    for (const [ten, giaTri] of Object.entries(banDau)) {
      if (giaTri === undefined) delete globalThis[ten]
      else globalThis[ten] = giaTri
    }
  }
})
