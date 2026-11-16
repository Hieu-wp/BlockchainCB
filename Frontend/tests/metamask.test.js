import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { chuanHoaChainId, goiVi, rutGonDiaChi, taoQuanLyVi, theoDoiMetaMask } from '../src/services/metamask.js'

const diaChi = `0x${'1'.repeat(40)}`, diaChiKhac = `0x${'2'.repeat(40)}`
const tick = () => new Promise(resolve => setTimeout(resolve, 0))
class Provider extends EventEmitter {
  isMetaMask = true
  accounts = []
  chain = '0x1'
  calls = []
  loi = null
  async request({ method }) {
    this.calls.push(method)
    if (method === 'eth_chainId') return this.chain
    if (method === 'eth_requestAccounts' && this.loi) throw this.loi
    if (['eth_accounts', 'eth_requestAccounts'].includes(method)) return this.accounts
    throw new Error('Không được gọi phương thức khác')
  }
}
function cuaSo(p, rdns = 'io.metamask') {
  const w = new EventTarget()
  if (p) w.addEventListener('eip6963:requestProvider', () => w.dispatchEvent(new CustomEvent('eip6963:announceProvider', { detail: { provider: p, info: { rdns } } })))
  return w
}
function tao(p, chain = '') {
  const q = taoQuanLyVi(cuaSo(p), chain)
  q.start()
  return q
}

test('Không có MetaMask: trạng thái chưa cài, không gọi ví hoặc mạng', async () => {
  const q = tao()
  await q.ketNoi()
  assert.equal(q.getSnapshot().trangThai, 'CHUA_CAI_DAT')
  assert.match(q.getSnapshot().loi, /Chưa phát hiện MetaMask/)
  q.stop()
})
test('Discovery ưu tiên EIP-6963 MetaMask, bỏ qua ví khác và không chọn trùng', () => {
  const meta = new Provider(), khac = new Provider(), w = cuaSo(khac, 'org.other')
  w.ethereum = khac
  const chon = []
  const cleanup = theoDoiMetaMask(w, p => chon.push(p))
  const announce = p => w.dispatchEvent(new CustomEvent('eip6963:announceProvider', { detail: { provider: p, info: { rdns: 'io.metamask' } } }))
  announce(meta); announce(meta); announce(new Provider())
  assert.deepEqual(chon, [meta])
  cleanup(); announce(new Provider()); assert.equal(chon.length, 1)
})
test('Fallback chọn đúng isMetaMask trong nhiều provider legacy', async () => {
  const p = new Provider(), khac = new Provider(); khac.isMetaMask = false
  const w = cuaSo(); w.ethereum = { providers: [khac, p] }
  const q = taoQuanLyVi(w); q.start()
  await new Promise(resolve => setTimeout(resolve, 230))
  assert.equal(q.getSnapshot().trangThai, 'CHUA_KET_NOI')
  assert.deepEqual(khac.calls, [])
  assert.ok(p.calls.includes('eth_accounts'))
  q.stop()
})
test('Khôi phục bằng eth_accounts, chỉ requestAccounts khi chủ động kết nối', async () => {
  const p = new Provider(), q = tao(p)
  await tick()
  assert.equal(q.getSnapshot().trangThai, 'CHUA_KET_NOI')
  assert.deepEqual(p.calls, ['eth_accounts', 'eth_chainId'])
  p.accounts = [diaChi]
  await q.ketNoi()
  assert.equal(q.getSnapshot().diaChi, diaChi)
  assert.equal(q.getSnapshot().trangThai, 'DA_KET_NOI')
  assert.equal(rutGonDiaChi(diaChi), '0x1111...1111')
  assert.equal(q.getSnapshot().chainYeuCau, '')
  q.stop()
  const qMoi = tao(p); await tick()
  assert.equal(qMoi.getSnapshot().diaChi, diaChi)
  assert.equal(p.calls.filter(m => m === 'eth_requestAccounts').length, 1)
  qMoi.stop()
})
test('Từ chối, pending, khóa ví và lỗi provider có thông báo Việt, thử lại được', async () => {
  for (const code of [4001, -32002, 4100, 4900, 12345]) {
    const p = new Provider(), q = tao(p); await tick()
    p.loi = { code, message: 'Không đưa lỗi kỹ thuật ra giao diện' }
    await q.ketNoi()
    assert.equal(q.getSnapshot().trangThai, 'LOI_KET_NOI')
    assert.ok(q.getSnapshot().loi.length > 20)
    assert.equal(q.getSnapshot().loi.includes(p.loi.message), false)
    p.loi = null; p.accounts = [diaChi]; await q.ketNoi()
    assert.equal(q.getSnapshot().trangThai, 'DA_KET_NOI'); q.stop()
  }
})
test('Theo dõi tài khoản/mạng/ngắt provider, dọn listener khi unmount', async () => {
  const p = new Provider(); p.accounts = [diaChi]
  const q = tao(p, '11155111'); await tick()
  assert.equal(q.getSnapshot().trangThai, 'SAI_MANG')
  q.start()
  for (const name of ['accountsChanged', 'chainChanged', 'disconnect']) assert.equal(p.listenerCount(name), 1)
  p.emit('accountsChanged', [diaChiKhac]); assert.equal(q.getSnapshot().diaChi, diaChiKhac)
  p.emit('chainChanged', '0xaa36a7'); assert.equal(q.getSnapshot().trangThai, 'DA_KET_NOI')
  p.emit('accountsChanged', []); assert.equal(q.getSnapshot().trangThai, 'CHUA_KET_NOI')
  p.emit('disconnect'); assert.equal(q.getSnapshot().diaChi, '')
  assert.equal(q.getSnapshot().trangThai, 'LOI_KET_NOI')
  q.stop()
  for (const name of ['accountsChanged', 'chainChanged', 'disconnect']) assert.equal(p.listenerCount(name), 0)
  q.start(); await tick()
  for (const name of ['accountsChanged', 'chainChanged', 'disconnect']) assert.equal(p.listenerCount(name), 1)
  q.stop()
})
test('Chỉ xác định sai mạng với cấu hình hợp lệ; địa chỉ/mạng sai không giả thành công', async () => {
  for (const config of ['', 'sai-chain']) {
    const p = new Provider(); p.accounts = [diaChi]
    const q = tao(p, config); await tick()
    assert.equal(q.getSnapshot().trangThai, 'DA_KET_NOI')
    assert.equal(q.getSnapshot().cauHinhSai, !!config)
    p.emit('chainChanged', 'khong-hop-le'); assert.equal(q.getSnapshot().trangThai, 'LOI_KET_NOI')
    q.stop()
  }
  assert.equal(chuanHoaChainId('11155111'), '0xaa36a7')
  for (const value of ['0', '-1', 'abc', {}, 1]) assert.equal(chuanHoaChainId(value), '')
  const p = new Provider(); p.accounts = ['dia-chi-sai']; const q = tao(p); await tick()
  assert.equal(q.getSnapshot().diaChi, ''); q.stop()
})
test('Ngắt liên kết ứng dụng bỏ qua sự kiện ví đến khi chủ động kết nối lại', async () => {
  const p = new Provider(); p.accounts = [diaChi]
  const q = tao(p); await tick()
  q.ngatLienKet(); p.emit('accountsChanged', [diaChiKhac]); p.emit('chainChanged', '0x89')
  assert.equal(q.getSnapshot().diaChi, '')
  assert.equal(q.getSnapshot().chainId, '')
  assert.match(q.getSnapshot().loi, /không bị thu hồi/)
  await q.ketNoi(); assert.equal(q.getSnapshot().diaChi, diaChi)
  q.stop()
})
test('Một popup mỗi lần; phản hồi muộn sau logout không đưa ví vào phiên Admin mới', async () => {
  const p = new Provider(), q = tao(p); await tick()
  let hoanTat
  const banDau = p.request.bind(p)
  p.request = ({ method }) => method === 'eth_requestAccounts'
    ? (p.calls.push(method), new Promise(resolve => { hoanTat = resolve })) : banDau({ method })
  const cho = q.ketNoi(); await tick()
  await q.ketNoi(); assert.equal(p.calls.filter(m => m === 'eth_requestAccounts').length, 1)
  assert.equal(q.getSnapshot().dangKetNoi, true)
  q.stop(); hoanTat([diaChi]); await cho
  assert.equal(q.getSnapshot().diaChi, '')
  const moi = tao(p); await tick(); assert.equal(moi.getSnapshot().diaChi, '')
  moi.stop()
})
test('Snapshot RPC cũ không ghi đè tài khoản/mạng mới từ sự kiện', async () => {
  const p = new Provider(); let traVe
  const banDau = p.request.bind(p)
  p.request = ({ method }) => method === 'eth_accounts' ? new Promise(resolve => { traVe = resolve }) : banDau({ method })
  const q = tao(p); await tick()
  p.emit('accountsChanged', [diaChiKhac]); p.emit('chainChanged', '0x89'); traVe([diaChi]); await tick()
  assert.equal(q.getSnapshot().diaChi, diaChiKhac)
  assert.equal(q.getSnapshot().chainId, '0x89'); q.stop()
})
test('Service chặn phương thức ký/gửi giao dịch, chỉ gọi ba RPC cho phép', async () => {
  const p = new Provider(), q = tao(p); await tick(); await q.ketNoi(); q.ngatLienKet(); q.stop()
  const truoc = p.calls.length
  for (const m of ['eth_sendTransaction', 'personal_sign', 'eth_sign', 'eth_signTypedData_v4', 'wallet_switchEthereumChain']) await assert.rejects(goiVi(p, m))
  assert.equal(p.calls.length, truoc)
  assert.ok(p.calls.every(m => ['eth_accounts', 'eth_requestAccounts', 'eth_chainId'].includes(m)))
})
