import test from 'node:test'
import assert from 'node:assert/strict'
import { coTheTuChoi, guiTuChoi, loiLyDo, taiTrangBacSi } from '../src/services/doctorRejection.js'

test('Từ chối bác sĩ dùng hợp đồng session/CSRF và không giả thành công', async t => {
  const cu = { fetch: globalThis.fetch, window: globalThis.window, DOMParser: globalThis.DOMParser }
  const bs = { maBacSi: 7, hoTen: 'Bác sĩ kiểm thử', trangThaiPheDuyet: 'PENDING' }
  const phien = { daXacThuc: true, vaiTro: 'ADMIN', danhSachQuyen: ['ROLE_ADMIN'] }
  globalThis.window = Object.assign(new EventTarget(), { location: { origin: 'http://localhost' } })
  globalThis.DOMParser = class { parseFromString(html) { return { querySelector: () => html.includes('_csrf') ? { value: 'token-test' } : null } } }
  const res = (data, status = 200, html = false) => {
    const r = new Response(html ? data : JSON.stringify(data), { status, headers: { 'content-type': html ? 'text/html' : 'application/json' } })
    Object.defineProperty(r, 'url', { value: 'http://localhost/api/admin/doctors/7/reject' })
    return r
  }
  try {
    await t.test('Chỉ hồ sơ PENDING có ID và tên hợp lệ được thao tác', () => {
      assert.equal(coTheTuChoi(bs), true)
      for (const b of [null, {}, { ...bs, maBacSi: -1 }, { ...bs, hoTen: ' ' }, { ...bs, trangThaiPheDuyet: 'APPROVED' }, { ...bs, trangThaiPheDuyet: 'REJECTED' }]) assert.equal(coTheTuChoi(b), false)
    })
    await t.test('Lý do rỗng, khoảng trắng và quá giới hạn không gọi mạng', async () => {
      globalThis.fetch = () => assert.fail('Không được gọi mạng')
      for (const lyDo of ['', '   ', 'a'.repeat(351)]) await assert.rejects(guiTuChoi(bs, lyDo), { status: 400 })
      assert.equal(loiLyDo('a'.repeat(350)), '')
      await assert.rejects(guiTuChoi({ ...bs, trangThaiPheDuyet: 'APPROVED' }, 'Lý do'), { status: 409 })
    })
    await t.test('Gửi đúng một POST, JSON đã trim, cookie và CSRF', async () => {
      const ds = []
      globalThis.fetch = async (url, opt) => {
        ds.push(url)
        assert.equal(opt.credentials, 'same-origin')
        if (url === '/login') return res('<input name="_csrf">', 200, true)
        assert.equal(url, '/api/admin/doctors/7/reject')
        assert.equal(opt.method, 'POST')
        assert.equal(opt.headers['X-CSRF-TOKEN'], 'token-test')
        assert.deepEqual(JSON.parse(opt.body), { lyDo: 'Thiếu chứng chỉ' })
        return res({ ...bs, trangThaiPheDuyet: 'REJECTED' })
      }
      assert.equal((await guiTuChoi(bs, ' Thiếu chứng chỉ ')).trangThaiPheDuyet, 'REJECTED')
      assert.equal(ds.length, 2)
    })
    await t.test('Thiếu CSRF không gửi POST', async () => {
      globalThis.fetch = async (url, opt) => { assert.equal(opt.method, 'GET'); return res('', 200, true) }
      await assert.rejects(guiTuChoi(bs, 'Lý do'), /CSRF/)
    })
    for (const status of [400, 403, 404, 409, 500]) await t.test(`HTTP ${status} không thành công và không tự POST lại`, async () => {
      let posts = 0
      globalThis.fetch = async (url) => {
        if (url === '/login') return res('<input name="_csrf">', 200, true)
        if (url === '/api/admin/me') return res(phien)
        posts++
        return res({ detail: 'Lỗi kiểm thử' }, status)
      }
      await assert.rejects(guiTuChoi(bs, 'Lý do'), { status })
      assert.equal(posts, 1)
    })
    await t.test('Phản hồi mất hoặc DTO sai không tạo thành công giả', async () => {
      for (const cheDo of ['mat-mang', 'dto-sai']) {
        let posts = 0
        globalThis.fetch = async url => {
          if (url === '/login') return res('<input name="_csrf">', 200, true)
          posts++
          if (cheDo === 'mat-mang') throw new TypeError('Failed to fetch')
          return res(bs)
        }
        await assert.rejects(guiTuChoi(bs, 'Lý do'))
        assert.equal(posts, 1)
      }
    })
    await t.test('Giữ bộ lọc, tự tải lại trang cuối hợp lệ khi tổng số trang giảm', async () => {
      const ds = []
      globalThis.fetch = async url => {
        const q = new URL(url, 'http://localhost').searchParams
        ds.push(q.get('trang'))
        assert.equal(q.get('tuKhoa'), 'bs')
        assert.equal(q.get('trangThaiPheDuyet'), 'PENDING')
        return res({ content: q.get('trang') === '0' ? [bs] : [], totalPages: 1, totalElements: 1 })
      }
      const page = await taiTrangBacSi({ trang: 1, kichThuoc: 10, tuKhoa: 'bs', trangThaiPheDuyet: 'PENDING' })
      assert.equal(page.trangHienTai, 0)
      assert.deepEqual(ds, ['1', '0'])
      assert.deepEqual(page.content, [bs])
    })
  } finally { Object.assign(globalThis, cu) }
})
