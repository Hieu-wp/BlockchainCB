import test from 'node:test'
import assert from 'node:assert/strict'
import { kiemTraHeThong } from '../src/services/trangThaiHeThong.js'

// Chỉ dùng phản hồi kiểm thử, không gọi Backend hoặc database.
test('Trạng thái hệ thống chỉ phản ánh các GET đã xác minh', async kiemThu => {
  const fetchCu = globalThis.fetch
  const windowCu = globalThis.window
  const cuaSo = new EventTarget()
  cuaSo.location = { origin: 'http://localhost:5173' }
  globalThis.window = cuaSo
  const duLieuHopLe = {
    '/api/admin/health': { trangThai: 'thanhCong' },
    '/api/admin/me': { daXacThuc: true, vaiTro: 'ADMIN', danhSachQuyen: ['ROLE_ADMIN', 'FACTOR_PASSWORD'] },
    '/api/admin/dashboard': { tongNguoiDung: 0 },
    '/api/admin/users': { danhSachNguoiDung: [] },
    '/api/admin/doctors/search': { content: [] },
    '/api/admin/audit-logs': { danhSachNhatKy: [] },
  }
  const phanHoi = (url, duLieu, status = 200) => {
    const ketQua = new Response(JSON.stringify(duLieu), { status, headers: { 'content-type': 'application/json' } })
    Object.defineProperty(ketQua, 'url', { value: `http://localhost:5173${url}` })
    return ketQua
  }
  try {
    await kiemThu.test('Kiểm tra đủ sáu GET có thật, giới hạn danh sách một dòng, không gọi Blockchain/POST', async () => {
      const danhSachDaGoi = []
      globalThis.fetch = async (url, tuyChon) => {
        danhSachDaGoi.push(url)
        assert.equal(tuyChon.method, 'GET')
        assert.equal(tuyChon.credentials, 'same-origin')
        const diaChi = new URL(url, cuaSo.location.origin)
        assert.ok(Object.hasOwn(duLieuHopLe, diaChi.pathname))
        if (['/api/admin/users', '/api/admin/doctors/search', '/api/admin/audit-logs'].includes(diaChi.pathname)) {
          assert.equal(diaChi.searchParams.get('kichThuoc'), '1')
          assert.equal(diaChi.searchParams.get('trang'), '0')
        }
        return phanHoi(url, duLieuHopLe[diaChi.pathname])
      }
      const ketQua = await kiemTraHeThong()
      assert.equal(danhSachDaGoi.length, 6)
      assert.equal(ketQua.ketQua.length, 6)
      assert.ok(ketQua.ketQua.every(muc => muc.trangThai === 'khaDung'))
      assert.ok(Number.isFinite(Date.parse(ketQua.thoiDiem)))
      // Không giữ nội dung người dùng/hồ sơ trả về trong kết quả giám sát.
      assert.ok(ketQua.ketQua.every(muc => !Object.hasOwn(muc, 'duLieu')))
    })
    await kiemThu.test('Một API lỗi không ngăn trả kết quả các API còn lại', async () => {
      globalThis.fetch = async url => {
        const duongDan = new URL(url, cuaSo.location.origin).pathname
        return phanHoi(url, duongDan === '/api/admin/dashboard' ? { error: 'Unavailable' } : duLieuHopLe[duongDan], duongDan === '/api/admin/dashboard' ? 503 : 200)
      }
      const { ketQua } = await kiemTraHeThong()
      assert.equal(ketQua.filter(muc => muc.trangThai === 'khaDung').length, 5)
      assert.equal(ketQua.find(muc => muc.duongDan === '/api/admin/dashboard').maHttp, 503)
    })
    await kiemThu.test('HTTP 2xx có DTO sai không được gắn nhãn khả dụng', async () => {
      globalThis.fetch = async url => phanHoi(url, {})
      const { ketQua } = await kiemTraHeThong()
      assert.ok(ketQua.every(muc => muc.trangThai === 'khongHopLe' && muc.coPhanHoi))
    })
    await kiemThu.test('Mất mạng không tạo trạng thái kết nối thành công', async () => {
      globalThis.fetch = async () => { throw new TypeError('Network unavailable') }
      const { ketQua } = await kiemTraHeThong()
      assert.ok(ketQua.every(muc => muc.trangThai === 'loi' && !muc.coPhanHoi))
    })
    await kiemThu.test('Thiếu ROLE_ADMIN không xác nhận phiên hợp lệ dù trả HTTP 200', async () => {
      globalThis.fetch = async url => {
        const duongDan = new URL(url, cuaSo.location.origin).pathname
        return phanHoi(url, duongDan === '/api/admin/me' ? { ...duLieuHopLe[duongDan], danhSachQuyen: ['ROLE_PATIENT'] } : duLieuHopLe[duongDan])
      }
      const { ketQua } = await kiemTraHeThong()
      assert.equal(ketQua.find(muc => muc.duongDan === '/api/admin/me').trangThai, 'khongHopLe')
    })
    await kiemThu.test('404/409 giữ nguyên mã lỗi, không trở thành thành công hoặc mất mạng', async () => {
      for (const ma of [404, 409]) {
        globalThis.fetch = async url => phanHoi(url, { message: 'Kiểm thử tài nguyên hoặc xung đột' }, ma)
        const { ketQua } = await kiemTraHeThong()
        assert.ok(ketQua.every(muc => muc.trangThai === 'loi' && muc.coPhanHoi && muc.maHttp === ma))
      }
    })
  } finally {
    globalThis.fetch = fetchCu
    if (windowCu === undefined) delete globalThis.window
    else globalThis.window = windowCu
  }
})
