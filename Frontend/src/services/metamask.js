// Chỉ kết nối/đọc tài khoản và mạng. Không cung cấp phương thức ký hoặc gửi giao dịch.
const phuongThucChoPhep = new Set(['eth_accounts', 'eth_requestAccounts', 'eth_chainId'])
export function goiVi(provider, method) {
  if (!phuongThucChoPhep.has(method)) return Promise.reject(new Error('Phương thức ví chưa được hỗ trợ.'))
  return Promise.resolve().then(() => provider.request({ method }))
}

export function chuanHoaChainId(value) {
  if (typeof value !== 'string' || !/^(0x[0-9a-f]+|[0-9]+)$/i.test(value.trim())) return ''
  try {
    const so = BigInt(value.trim())
    return so > 0n && so < 2n ** 256n ? `0x${so.toString(16)}` : ''
  } catch { return '' }
}

export function diaChiDau(accounts) {
  return Array.isArray(accounts) && typeof accounts[0] === 'string' && /^0x[0-9a-f]{40}$/i.test(accounts[0]) ? accounts[0] : ''
}
export const rutGonDiaChi = diaChi => diaChi ? `${diaChi.slice(0, 6)}...${diaChi.slice(-4)}` : ''
export function tenMang(chainId) {
  return ({ '0x1': 'Ethereum Mainnet', '0xaa36a7': 'Sepolia', '0x89': 'Polygon', '0x38': 'BNB Smart Chain', '0xa': 'Optimism', '0xa4b1': 'Arbitrum One', '0x2105': 'Base', '0x7a69': 'Mạng phát triển cục bộ (31337)' })[chainId] || 'Mạng chưa xác định'
}
export function thongBaoLoiVi(loi) {
  switch (Number(loi?.code)) {
    case 4001: return 'Bạn đã từ chối kết nối MetaMask. Có thể nhấn kết nối để thử lại.'
    case 32002: case -32002: return 'Một yêu cầu đang chờ trong MetaMask. Hãy mở tiện ích để hoàn tất hoặc hủy yêu cầu đó.'
    case 4100: return 'MetaMask chưa cấp quyền tài khoản. Hãy mở khóa ví và cho phép MediCare kết nối.'
    case 4900: case 4901: return 'MetaMask mất kết nối mạng. Hãy kiểm tra tiện ích và thử kết nối lại.'
    default: return 'Không kết nối được MetaMask. Hãy mở khóa ví, kiểm tra tiện ích và thử lại.'
  }
}

const hopLe = p => p && typeof p.request === 'function' && typeof p.on === 'function' && typeof p.removeListener === 'function'

export function theoDoiMetaMask(cuaSo, chon) {
  let daChon6963 = false
  const viKhac = new Set()
  function thongBao(e) {
    const { info, provider } = e.detail || {}
    if (!hopLe(provider)) return
    if (info?.rdns !== 'io.metamask') { viKhac.add(provider); return }
    if (!daChon6963) { daChon6963 = true; chon(provider) }
  }
  function legacy() {
    if (daChon6963) return
    const goc = cuaSo.ethereum
    const ds = Array.isArray(goc?.providers) ? goc.providers : [goc]
    const ungVien = [...new Set(ds)].filter(p => hopLe(p) && p.isMetaMask === true && !viKhac.has(p))
    if (ungVien.length === 1) chon(ungVien[0])
  }
  cuaSo.addEventListener('eip6963:announceProvider', thongBao)
  cuaSo.addEventListener('ethereum#initialized', legacy)
  cuaSo.dispatchEvent(new Event('eip6963:requestProvider'))
  const hen = setTimeout(legacy, 200)
  return () => {
    clearTimeout(hen)
    cuaSo.removeEventListener('eip6963:announceProvider', thongBao)
    cuaSo.removeEventListener('ethereum#initialized', legacy)
  }
}

// Store riêng cho mỗi lần mount giao diện Admin, không lưu ví vào storage/database.
export function taoQuanLyVi(cuaSo, mangCauHinh = '') {
  const chainYeuCau = chuanHoaChainId(mangCauHinh)
  let state = { trangThai: 'CHUA_CAI_DAT', diaChi: '', chainId: '', loi: '', dangKetNoi: false, chainYeuCau, cauHinhSai: !!mangCauHinh && !chainYeuCau }
  const subscribers = new Set()
  let provider, huyDiscovery, huySuKien, hoatDong = false, boQua = false, phien = 0, doiTaiKhoan = 0, doiMang = 0, dangCho = false
  function capNhat(moi) {
    state = { ...state, ...moi }
    if (!moi.trangThai) state.trangThai = state.diaChi
      ? (chainYeuCau && state.chainId && state.chainId !== chainYeuCau ? 'SAI_MANG' : 'DA_KET_NOI') : 'CHUA_KET_NOI'
    for (const fn of subscribers) fn()
  }
  function conHieuLuc(ma) { return hoatDong && phien === ma && !boQua }
  async function dongBo(yeuCau = false) {
    const ma = phien, p = provider, a = doiTaiKhoan, c = doiMang
    try {
      const accounts = await goiVi(p, yeuCau ? 'eth_requestAccounts' : 'eth_accounts')
      if (!conHieuLuc(ma)) return
      const chain = chuanHoaChainId(await goiVi(p, 'eth_chainId'))
      if (!chain) throw new Error('Chain ID không hợp lệ')
      if (!conHieuLuc(ma)) return
      const moi = { loi: '' }
      if (a === doiTaiKhoan) moi.diaChi = diaChiDau(accounts)
      if (c === doiMang) moi.chainId = chain
      if (!diaChiDau(accounts) && a === doiTaiKhoan) moi.loi = 'MetaMask không cung cấp tài khoản. Hãy mở khóa ví và cấp quyền kết nối.'
      capNhat(moi)
    } catch (loi) {
      if (conHieuLuc(ma)) capNhat({ trangThai: 'LOI_KET_NOI', diaChi: '', chainId: '', loi: thongBaoLoiVi(loi) })
    }
  }
  function chonProvider(p) {
    if (!hoatDong || provider === p) return
    phien++
    huySuKien?.()
    provider = p
    capNhat({ trangThai: 'CHUA_KET_NOI', diaChi: '', chainId: '', loi: '' })
    const taiKhoan = accounts => {
      if (!hoatDong || boQua) return
      doiTaiKhoan++
      capNhat({ diaChi: diaChiDau(accounts), loi: diaChiDau(accounts) ? '' : 'MetaMask không còn cung cấp tài khoản. Hãy mở khóa ví hoặc kết nối lại.' })
    }
    const mang = chain => {
      if (!hoatDong || boQua) return
      doiMang++
      const chainId = chuanHoaChainId(chain)
      capNhat(chainId ? { chainId, loi: '' } : { chainId: '', trangThai: 'LOI_KET_NOI', loi: 'MetaMask trả về thông tin mạng không hợp lệ. Hãy kết nối lại.' })
    }
    const ngat = () => {
      if (!hoatDong || boQua) return
      phien++
      capNhat({ trangThai: 'LOI_KET_NOI', diaChi: '', chainId: '', loi: 'MetaMask đã ngắt kết nối. Hãy kiểm tra tiện ích và kết nối lại.' })
    }
    p.on('accountsChanged', taiKhoan); p.on('chainChanged', mang); p.on('disconnect', ngat)
    huySuKien = () => { p.removeListener('accountsChanged', taiKhoan); p.removeListener('chainChanged', mang); p.removeListener('disconnect', ngat) }
    if (!boQua) void dongBo()
  }
  return {
    subscribe: fn => { subscribers.add(fn); return () => subscribers.delete(fn) },
    getSnapshot: () => state,
    start() {
      if (hoatDong) return
      hoatDong = true
      huyDiscovery = theoDoiMetaMask(cuaSo, chonProvider)
    },
    stop() { hoatDong = false; phien++; huyDiscovery?.(); huySuKien?.(); provider = null },
    async ketNoi() {
      if (dangCho || !hoatDong) return
      if (!provider) {
        capNhat({ trangThai: 'CHUA_CAI_DAT', loi: 'Chưa phát hiện MetaMask. Hãy cài hoặc bật tiện ích MetaMask rồi tải lại trang.' })
        return
      }
      dangCho = true; boQua = false; phien++
      capNhat({ trangThai: 'DANG_KET_NOI', dangKetNoi: true, loi: '' })
      try { await dongBo(true) }
      finally {
        dangCho = false
        if (hoatDong) capNhat({ trangThai: state.trangThai, dangKetNoi: false })
      }
    },
    ngatLienKet() {
      if (dangCho) return
      boQua = true; phien++
      capNhat({ trangThai: provider ? 'CHUA_KET_NOI' : 'CHUA_CAI_DAT', diaChi: '', chainId: '', loi: 'Đã ngắt liên kết trong MediCare. Quyền của website trong MetaMask không bị thu hồi.' })
    },
  }
}
