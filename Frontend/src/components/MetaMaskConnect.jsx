import { useState } from 'react'
import { Wallet, Copy, Unplug } from 'lucide-react'
import { Link } from 'react-router-dom'
import { rutGonDiaChi, tenMang } from '../services/metamask'
import { Hop } from './Shared'

const nhan = { CHUA_CAI_DAT: 'Chưa phát hiện MetaMask', CHUA_KET_NOI: 'Chưa kết nối', DANG_KET_NOI: 'Đang kết nối...', DA_KET_NOI: 'Đã kết nối ví', SAI_MANG: 'Sai mạng yêu cầu', LOI_KET_NOI: 'Lỗi kết nối ví' }

export default function MetaMaskConnect({ vi, chiTiet = false }) {
  const [daSaoChep, setDaSaoChep] = useState('')
  const [loiSaoChep, setLoiSaoChep] = useState('')
  const nut = <button type="button" className="btn btn-outline wallet-connect" disabled={vi.dangKetNoi} onClick={vi.ketNoi} title={vi.diaChi ? `Ví MetaMask ${vi.diaChi}` : 'Kết nối MetaMask'}><Wallet size={17} aria-hidden="true"/><span>{vi.dangKetNoi ? 'Đang kết nối...' : vi.diaChi ? rutGonDiaChi(vi.diaChi) : 'Kết nối MetaMask'}</span></button>
  if (!chiTiet) return <div className="wallet-header">{nut}{(vi.loi || vi.trangThai === 'SAI_MANG') && <div role="alert" className="wallet-feedback">{vi.loi || 'Mạng hiện tại khác mạng yêu cầu. Hãy chọn mạng phù hợp trong MetaMask.'}<Link to="/profile">Xem chi tiết ví trong Hồ sơ Admin</Link></div>}</div>
  async function saoChep() {
    try { await navigator.clipboard.writeText(vi.diaChi); setDaSaoChep(vi.diaChi); setLoiSaoChep('') }
    catch { setLoiSaoChep('Không sao chép được. Bạn có thể chọn và sao chép địa chỉ trực tiếp.') }
  }
  return <Hop tieuDe="Ví Blockchain" phuDe="MetaMask · Kết nối độc lập với phiên Admin">
    <div className="wallet-profile" aria-live="polite">
      <span className={`pill ${vi.trangThai === 'DA_KET_NOI' ? 'success' : 'warning'}`}>{nhan[vi.trangThai]}</span>
      <dl className="detail-grid"><div><dt>Địa chỉ ví</dt><dd className="wallet-address">{vi.diaChi || 'Chưa có tài khoản được cấp quyền'}</dd></div>
        <div><dt>Chain ID</dt><dd>{vi.chainId ? `${vi.chainId} (${BigInt(vi.chainId).toString()})` : 'Chưa nhận được'}</dd></div>
        <div><dt>Tên mạng</dt><dd>{vi.chainId ? tenMang(vi.chainId) : 'Chưa xác định'}</dd></div>
        <div><dt>Mạng yêu cầu</dt><dd>{vi.chainYeuCau ? `${tenMang(vi.chainYeuCau)} (${vi.chainYeuCau})` : vi.cauHinhSai ? 'Cấu hình mạng yêu cầu không hợp lệ' : 'Chưa cấu hình mạng yêu cầu'}</dd></div></dl>
      {vi.loi && <p className="login-error" role="alert">{vi.loi}</p>}
      {vi.trangThai === 'SAI_MANG' && <p className="login-error" role="alert">Mạng hiện tại khác mạng yêu cầu. Hãy chọn mạng phù hợp trong MetaMask.</p>}
      {vi.dangKetNoi && <p role="status">Mở MetaMask để hoàn tất hoặc hủy yêu cầu kết nối đang chờ.</p>}
      <div className="row-actions">{nut}<button type="button" className="btn btn-outline" disabled={!vi.diaChi || vi.dangKetNoi} onClick={saoChep}><Copy size={16}/> Sao chép địa chỉ</button><button type="button" className="btn btn-outline" disabled={!vi.diaChi || vi.dangKetNoi} onClick={vi.ngatLienKet}><Unplug size={16}/> Ngắt liên kết trong MediCare</button></div>
      {((daSaoChep && daSaoChep === vi.diaChi) || loiSaoChep) && <p role="status" className="field-help">{loiSaoChep || 'Đã sao chép địa chỉ ví.'}</p>}
      <p className="field-help">Ngắt liên kết chỉ xóa trạng thái trong giao diện MediCare; không thu hồi quyền website trong MetaMask. Kết nối ví không chứng minh quyền Admin trên Smart Contract. Chưa xác minh quyền owner hoặc vai trò contract.</p>
    </div>
  </Hop>
}
