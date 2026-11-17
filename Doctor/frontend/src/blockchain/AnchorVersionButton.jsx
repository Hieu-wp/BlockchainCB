import { useEffect, useRef, useState } from 'react';
import { anchorApi, walletError } from './anchorApi';
import { anchorVersion, watchWallet } from './medicalRecordContract';
const labels = { NOT_SUBMITTED: 'Chưa gửi', PREPARED: 'Đã chuẩn bị · đang chờ ví', PENDING: 'Đang chờ xác nhận', CONFIRMED: 'Đã được Backend xác minh', FAILED: 'Giao dịch thất bại / không khớp' };
export default function AnchorVersionButton({ recordId, versionNumber, linkedWallet, integrity, authorAllowed }) {
  const [status, setStatus] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(''), [hash, setHash] = useState(null), [stale, setStale] = useState(false);
  const lock = useRef(false), epoch = useRef(0);
  async function refresh() { try { const result = await anchorApi.status(recordId, versionNumber); setStatus(result); setError(''); } catch (e) { setError(walletError(e)); } }
  useEffect(() => { let cancelled = false; anchorApi.status(recordId, versionNumber).then(s => { if (!cancelled) setStatus(s); }).catch(e => { if (!cancelled) setError(walletError(e)); }); return () => { cancelled = true; }; }, [recordId, versionNumber]);
  useEffect(() => watchWallet(() => { epoch.current++; setStale(true); setError('MetaMask account/chain đã đổi. Kiểm tra ví rồi bấm xác nhận lại trước khi gửi.'); }), []);
  useEffect(() => {
    if (!['PENDING', 'PREPARED'].includes(status?.status)) return;
    let count = 0; const timer = setInterval(() => { if (++count > 24) { clearInterval(timer); setError('Chờ xác nhận quá lâu. Bạn có thể kiểm tra lại; không tự gửi thêm giao dịch.'); } else refresh(); }, 5000);
    return () => clearInterval(timer);
  }, [status?.status, recordId, versionNumber]);
  async function send() {
    if (lock.current) return; lock.current = true; setBusy(true); setError(''); const current = epoch.current;
    try { setStatus(await anchorVersion({ id: recordId, number: versionNumber, linkedWallet, onHash: h => { setHash(h); setStatus(s => ({ ...s, status: 'PENDING' })); }, stillCurrent: () => epoch.current === current })); }
    catch (e) { setError(walletError(e)); } finally { setBusy(false); lock.current = false; }
  }
  const confirmed = status?.status === 'CONFIRMED';
  const pending = ['PENDING', 'PREPARED'].includes(status?.status) && !(status?.status === 'PREPARED' && status.preparedAt && Date.now() - Date.parse(status.preparedAt) > 120000);
  const tx = status?.transactionHash || hash;
  return <div className="anchor-panel"><h3>Ethereum Sepolia</h3><p role="status">{labels[status?.status] || 'Đang kiểm tra trạng thái…'}</p>
    {status && !status.enabled && <p className="muted">Blockchain đang tắt; cần review deployment/admin key và cấu hình Backend.</p>}
    {!linkedWallet && <p>Liên kết ví bằng chữ ký trong Hồ sơ cá nhân trước khi ghi.</p>}
    {!authorAllowed && <p className="muted">Chỉ tác giả phiên bản được gửi bằng ví đã xác minh.</p>}
    <button type="button" disabled={busy || !status?.enabled || !integrity || !linkedWallet || !authorAllowed || confirmed || pending || stale} onClick={send}>{busy ? 'Đang chờ MetaMask / Backend…' : 'Ghi SHA-256 lên Blockchain'}</button>
    <button type="button" className="secondary" disabled={busy} onClick={refresh}>Kiểm tra / phục hồi trạng thái</button>
    {stale && <button type="button" className="secondary" onClick={() => setStale(false)}>Đã kiểm tra ví — cho phép xác thực lại</button>}
    {tx && <p><a href={`https://sepolia.etherscan.io/tx/${tx}`} target="_blank" rel="noreferrer">Xem giao dịch trên Etherscan ↗</a><code>{tx}</code></p>}
    {!confirmed && tx && <small>Transaction hash chưa phải xác nhận thành công.</small>}
    {error && <div role="alert" className="alert error">{error}</div>}
  </div>;
}
