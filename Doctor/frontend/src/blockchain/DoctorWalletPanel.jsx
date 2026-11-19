import { useEffect, useRef, useState } from 'react';
import { connectDoctorWallet, linkDoctorWallet, watchWallet } from './medicalRecordContract';
import { anchorApi, walletError } from './anchorApi';
export default function DoctorWalletPanel({ linkedWallet, approvalStatus, onLinked }) {
  const [wallet, setWallet] = useState(null), [proof, setProof] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const epoch = useRef(0), lock = useRef(false);
  const eligible = ['PENDING', 'APPROVED'].includes(approvalStatus);
  useEffect(() => {
    let cancelled = false;
    anchorApi.wallet().then(result => { if (!cancelled) setProof(result); }).catch(e => { if (!cancelled) setError(walletError(e)); });
    return () => { cancelled = true; };
  }, [linkedWallet]);
  useEffect(() => watchWallet(async () => {
    const current = ++epoch.current;
    setWallet(null); setSuccess(''); setError('');
    try {
      // Re-read authorised accounts only; never open a signing/connection prompt on an event.
      const result = await connectDoctorWallet({ requestAccess: false });
      if (current === epoch.current) setWallet(result);
    } catch (e) { if (current === epoch.current) setError(walletError(e)); }
  }), []);
  async function perform(link) {
    if (lock.current) return; lock.current = true; setBusy(true); setError(''); setSuccess(''); const current = epoch.current;
    try {
      if (link) {
        await linkDoctorWallet(wallet.address, () => epoch.current === current);
        setSuccess('Backend đã xác minh chữ ký và liên kết ví.');
        // Reload /me rather than locally inventing wallet/approval/role state.
        await onLinked?.();
        setProof(await anchorApi.wallet());
      }
      else { const result = await connectDoctorWallet(); if (current === epoch.current) setWallet(result); }
    } catch (e) { setError(walletError(e)); } finally { lock.current = false; setBusy(false); }
  }
  const savedWallet = proof ? proof.walletAddress : linkedWallet;
  const wrongWallet = proof?.verified && wallet && savedWallet?.toLowerCase() !== wallet.address.toLowerCase();
  const alreadyVerified = proof?.verified && wallet && !wrongWallet;
  return <section className="wallet-panel"><h3>Ví Blockchain Doctor</h3><p>Ví đã lưu tại Backend: <code>{savedWallet || 'Chưa liên kết'}</code></p>
    <p className="muted">Kết nối MetaMask chỉ đọc địa chỉ. Chữ ký challenge mới là bằng chứng sở hữu ví.</p>
    {proof?.verified ? <p>Backend đã xác minh quyền sở hữu ví.</p> : <p>Bạn cần liên kết ví MetaMask để Admin có thể xét duyệt và cấp quyền Blockchain.</p>}
    {!eligible && <p role="alert">Trạng thái hồ sơ không cho phép liên kết ví. Hãy liên hệ Admin.</p>}
    <button type="button" disabled={busy || !eligible} onClick={() => perform(false)}>{busy ? 'Đang xử lý…' : 'Kết nối MetaMask'}</button>
    {wallet && <><p>Đã kết nối MetaMask: <code>{wallet.address}</code> · Sepolia</p>
      <p>{wallet.hasDoctorRole === true ? 'Vai trò Blockchain của ví đang kết nối: Có DOCTOR_ROLE' : wallet.hasDoctorRole === false ? 'Vai trò Blockchain của ví đang kết nối: Chưa có DOCTOR_ROLE' : 'Chưa đọc được DOCTOR_ROLE. Bạn vẫn có thể chứng minh sở hữu ví; thử kiểm tra vai trò lại sau.'}</p>
      {proof?.verified && !wrongWallet && wallet.hasDoctorRole === false && <p role="status">Ví đã liên kết thành công. Đang chờ Admin cấp quyền Blockchain.</p>}
      {wrongWallet && <p role="alert">Sai ví Doctor. Chọn đúng ví đã xác minh tại Backend rồi kết nối lại.</p>}
      <button type="button" disabled={busy || !eligible || wrongWallet || alreadyVerified} onClick={() => perform(true)}>{alreadyVerified ? 'Ví này đã được xác minh' : 'Ký challenge và liên kết ví'}</button></>}
    {error && <div role="alert" className="alert error">{error}</div>}{success && <div role="status" className="alert success">{success}</div>}
  </section>;
}
