import { useEffect, useRef, useState } from 'react';
import { connectDoctorWallet, linkDoctorWallet, watchWallet } from './medicalRecordContract';
import { walletError } from './anchorApi';
export default function DoctorWalletPanel({ linkedWallet, onLinked }) {
  const [wallet, setWallet] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const epoch = useRef(0), lock = useRef(false);
  useEffect(() => watchWallet(() => { epoch.current++; setWallet(null); setSuccess(''); setError('MetaMask account hoặc mạng đã đổi. Hãy kết nối lại.'); }), []);
  async function perform(link) {
    if (lock.current) return; lock.current = true; setBusy(true); setError(''); setSuccess(''); const current = epoch.current;
    try {
      if (link) { const result = await linkDoctorWallet(wallet.address, () => epoch.current === current); setSuccess('Backend đã xác minh chữ ký và liên kết ví.'); await onLinked?.(result.walletAddress); }
      else { const result = await connectDoctorWallet(); if (current === epoch.current) setWallet(result); }
    } catch (e) { setError(walletError(e)); } finally { lock.current = false; setBusy(false); }
  }
  return <section className="wallet-panel"><h3>Ví Blockchain Doctor</h3><p>Ví đã lưu tại Backend: <code>{linkedWallet || 'Chưa liên kết'}</code></p><p className="muted">Kết nối MetaMask chỉ đọc địa chỉ. Chữ ký challenge mới là bằng chứng sở hữu ví.</p>
    <button type="button" disabled={busy} onClick={() => perform(false)}>{busy ? 'Đang xử lý…' : 'Kết nối MetaMask'}</button>
    {wallet && <><p>Tài khoản: <code>{wallet.address}</code> · Sepolia</p><p>{wallet.hasDoctorRole ? 'Có DOCTOR_ROLE' : 'Chưa có DOCTOR_ROLE — liên hệ Admin'}</p><button type="button" disabled={busy} onClick={() => perform(true)}>Ký challenge và liên kết ví</button></>}
    {error && <div role="alert" className="alert error">{error}</div>}{success && <div role="status" className="alert success">{success}</div>}
  </section>;
}
