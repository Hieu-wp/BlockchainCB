import test from 'node:test'
import assert from 'node:assert/strict'
import { dangXuat } from '../src/services/api.js'

test('Đăng xuất chỉ hoàn tất khi Backend xác nhận không còn phiên Admin', async kiemThu => {
  const banDau = { fetch: globalThis.fetch, window: globalThis.window, DOMParser: globalThis.DOMParser }
  globalThis.window = { location: { origin: 'http://localhost:5173' } }
  globalThis.DOMParser = class {
    parseFromString(html) { return { querySelector: () => html.includes('csrf-test') ? { value: 'csrf-test' } : null } }
  }
  const phanHoi = (url, data, status = 200, kieu = 'application/json') => {
    const phanHoi = new Response(kieu.includes('json') ? JSON.stringify(data) : data, { status, headers: { 'content-type': kieu } })
    Object.defineProperty(phanHoi, 'url', { value: new URL(url, window.location.origin).href })
    return phanHoi
  }
  const csrf = () => phanHoi('/login', '<input name="_csrf" value="csrf-test">', 200, 'text/html')
  const admin = { daXacThuc: true, vaiTro: 'ADMIN', danhSachQuyen: ['ROLE_ADMIN'] }
  try {
    await kiemThu.test('Opaque redirect của POST vẫn cần GET /me với cookie, không cache', async () => {
      const cacLan = []
      globalThis.fetch = async (url, tuyChon) => {
        cacLan.push(url)
        assert.equal(tuyChon.credentials, 'same-origin')
        if (url === '/login') return csrf()
        if (url === '/logout') {
          assert.equal(tuyChon.method, 'POST'); assert.equal(tuyChon.redirect, 'manual')
          assert.equal(tuyChon.body.get('_csrf'), 'csrf-test')
          return { type: 'opaqueredirect', status: 0, ok: false }
        }
        assert.equal(tuyChon.cache, 'no-store')
        return phanHoi('/login', '<input name="_csrf" value="csrf-test">', 200, 'text/html')
      }
      assert.equal(await dangXuat(), true)
      assert.deepEqual(cacLan, ['/login', '/logout', '/api/admin/me'])
    })
    await kiemThu.test('HTTP 200 không đủ nếu phiên Admin vẫn hợp lệ', async () => {
      globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, url === '/api/admin/me' ? admin : {})
      await assert.rejects(dangXuat(), /Phiên Admin vẫn còn hiệu lực/)
    })
    await kiemThu.test('403 CSRF không được coi là thành công và không tự POST lại', async () => {
      let soPost = 0
      globalThis.fetch = async url => {
        if (url === '/login') return csrf()
        assert.equal(url, '/logout'); soPost += 1
        return phanHoi(url, {}, 403)
      }
      await assert.rejects(dangXuat(), { status: 403, message: /CSRF/ })
      assert.equal(soPost, 1)
    })
    await kiemThu.test('Thiếu CSRF không gửi POST', async () => {
      globalThis.fetch = async url => { assert.equal(url, '/login'); return phanHoi(url, '', 200, 'text/html') }
      await assert.rejects(dangXuat(), /Không tìm thấy CSRF token/)
    })
    await kiemThu.test('Mất mạng khi lấy CSRF không giả đăng xuất', async () => {
      globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
      await assert.rejects(dangXuat(), /Không kết nối được Backend/)
    })
    await kiemThu.test('Mất mạng khi POST có thông báo riêng và chỉ gửi một lần', async () => {
      let soPost = 0
      globalThis.fetch = async url => {
        if (url === '/login') return csrf()
        soPost += 1; throw new TypeError('Failed to fetch')
      }
      await assert.rejects(dangXuat(), /Chưa xác nhận phiên đã được hủy/)
      assert.equal(soPost, 1)
    })
    await kiemThu.test('Mất mạng khi xác minh không báo thành công dù POST trả 200', async () => {
      globalThis.fetch = async url => {
        if (url === '/login') return csrf()
        if (url === '/logout') return phanHoi(url, {})
        throw new TypeError('Failed to fetch')
      }
      await assert.rejects(dangXuat(), /không xác minh được phiên do lỗi mạng/)
    })
    await kiemThu.test('5xx của POST hoặc kiểm tra phiên vẫn là lỗi, không phải mất phiên', async () => {
      for (const duongDanLoi of ['/logout', '/api/admin/me']) {
        globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, {}, url === duongDanLoi ? 503 : 200)
        await assert.rejects(dangXuat(), { status: 503 })
      }
    })
    await kiemThu.test('401/403 từ GET /me xác nhận không còn truy cập Admin', async () => {
      for (const ma of [401, 403]) {
        globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, {}, url === '/api/admin/me' ? ma : 200)
        assert.equal(await dangXuat(), true)
      }
    })
    await kiemThu.test('POST 401 vẫn phải xác minh /me, không giữ Admin khi phiên đã hết', async () => {
      globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, {}, 401)
      assert.equal(await dangXuat(), true)
      globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, url === '/api/admin/me' ? admin : {}, url === '/logout' ? 401 : 200)
      await assert.rejects(dangXuat(), /Phiên Admin vẫn còn hiệu lực/)
    })
    await kiemThu.test('HTML hoặc JSON không đúng hợp đồng không chứng minh đăng xuất', async () => {
      for (const kieu of ['text/html', 'application/json']) {
        globalThis.fetch = async url => url === '/login' ? csrf() : phanHoi(url, kieu.includes('json') ? {} : '<html>Frontend</html>', 200, kieu)
        await assert.rejects(dangXuat(), /không xác nhận được trạng thái/)
      }
    })
    await kiemThu.test('Các caller cùng lúc dùng một POST, khóa được mở sau khi lỗi để thử lại chủ động', async () => {
      let soPost = 0
      globalThis.fetch = async url => {
        if (url === '/login') return csrf()
        if (url === '/logout') { soPost += 1; return phanHoi(url, {}) }
        return phanHoi(url, admin)
      }
      const ketQua = await Promise.allSettled([dangXuat(), dangXuat(), dangXuat()])
      assert.ok(ketQua.every(muc => muc.status === 'rejected'))
      assert.equal(soPost, 1)
      await assert.rejects(dangXuat(), /Phiên Admin vẫn còn hiệu lực/)
      assert.equal(soPost, 2)
    })
  } finally {
    for (const [ten, giaTri] of Object.entries(banDau)) {
      if (giaTri === undefined) delete globalThis[ten]
      else globalThis[ten] = giaTri
    }
  }
})
