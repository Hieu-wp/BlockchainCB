import { useEffect, useRef, useState } from 'react';
import { api } from './api';
export const sharedDisabledMessage = 'Luồng cấp quyền chung đang tắt. Khởi động Doctor Backend với profile shared-grants để dùng nguồn quyền public.';
const messages = {
  SHARED_GRANTS_DISABLED: sharedDisabledMessage,
  SHARED_SCHEMA_UNVERIFIED: 'Backend chưa xác minh được cấu trúc dữ liệu Patient. Kiểm tra phiên bản Backend và metadata của bảng chung.',
  SHARED_METADATA_UNAVAILABLE: 'Backend không đọc được metadata database. Kiểm tra kết nối, giới hạn Pooler và quyền truy cập database.',
  PATIENT_CODE_UNAVAILABLE: 'Không tìm thấy mã bệnh nhân khả dụng. Kiểm tra lại mã với bệnh nhân.',
  DUPLICATE_GRANTS_REVIEW_REQUIRED: 'Cặp Doctor–Patient có nhiều yêu cầu đang mở. Nhờ nhóm kiểm tra, không gửi thêm.',
  ACCESS_REQUEST_SCHEMA_CONFLICT: 'Không thể tạo yêu cầu do ràng buộc dữ liệu chung. Yêu cầu cũ được giữ nguyên; cần Patient phối hợp quy trình xin lại.',
};
export const integrationError = e => messages[e?.code] || e?.message || 'Không thể tải yêu cầu cấp quyền.';
function status(g) {
  if (g.revoked || g.status === 'REVOKED') return 'Đã thu hồi';
  if (g.expired === true) return 'Đã hết hạn';
  if (g.status === 'ACTIVE' && g.expired !== false) return 'Chờ xác minh hiệu lực';
  return { PENDING: 'Chờ bệnh nhân duyệt', ACTIVE: 'Patient đã cấp quyền', REJECTED: 'Bệnh nhân từ chối' }[g.status] || g.status;
}
export default function AccessGrantRequests({ onChanged }) {
  const [config, setConfig] = useState(null), [code, setCode] = useState(''), [page, setPage] = useState(0);
  const [data, setData] = useState(null), [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const lock = useRef(false), sequence = useRef(0), reading = useRef(null);
  useEffect(() => {
    const abort = new AbortController();
    api.integration(abort.signal).then(setConfig).catch(e => { if (e.name !== 'AbortError') setError(integrationError(e)); });
    return () => { abort.abort(); sequence.current++; };
  }, []);
  async function load() {
    if (reading.current?.page === page) return reading.current.promise;
    const current = ++sequence.current;
    const task = (async () => {
    try { const next = await api.accessRequests(page); if (current === sequence.current) { setData(next); setError(''); } }
    catch (e) { if (current === sequence.current) { setData(null); setError(integrationError(e)); } }
    })();
    reading.current = {page,promise:task};
    try { await task; } finally { if (reading.current?.promise === task) reading.current = null; }
  }
  useEffect(() => {
    if (!config?.sharedGrantsEnabled || !config.schemaReady) return;
    load(); const update = () => { if (!document.hidden) load(); }; const timer = setInterval(update, 30000); window.addEventListener('focus', update);
    return () => { sequence.current++; clearInterval(timer); window.removeEventListener('focus', update); };
  }, [config?.sharedGrantsEnabled, config?.schemaReady, page]);
  async function submit(e) {
    e.preventDefault(); if (lock.current) return; lock.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const result = await api.requestAccess(code.trim());
      setNotice(result.status === 'ACTIVE' ? 'Bạn đã có quyền ACTIVE còn hiệu lực.' : result.existing ? 'Yêu cầu đã tồn tại. Đang chờ bệnh nhân duyệt.' : 'Đã gửi yêu cầu. Đang chờ bệnh nhân duyệt.');
      await load(); onChanged?.();
    } catch (e) { setError(integrationError(e)); } finally { setBusy(false); lock.current = false; }
  }
  const ready = config?.sharedGrantsEnabled && config.schemaReady;
  return <section className="panel"><div className="section-head"><h2>Xin quyền khám bằng mã bệnh nhân</h2></div>
    {!config && !error && <p role="status">Đang kiểm tra cấu hình tích hợp Patient…</p>}
    {config && !ready && <p className="muted">{sharedDisabledMessage}</p>}
    {ready && <p className="muted">Yêu cầu được gửi vào nguồn quyền chung. Chỉ ACTIVE còn hiệu lực mới xuất hiện trong danh sách; tạo bệnh án chung còn chờ đối chiếu schema và quyền ghi.</p>}
    <form className="search" onSubmit={submit}><label>Mã bệnh nhân<input aria-label="Mã bệnh nhân để xin quyền" value={code} onChange={e => setCode(e.target.value)} required maxLength={128} disabled={!ready || busy}/></label>
      <button disabled={!ready || busy || !code.trim()}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu cấp quyền khám'}</button></form>
    {notice && <p role="status" className="alert success">{notice}</p>}{error && <p role="alert" className="alert error">{error}</p>}
    {ready && <><button className="secondary" onClick={() => { load(); onChanged?.(); }}>Kiểm tra Patient đã duyệt</button>
      {!data && !error && <p role="status">Đang tải yêu cầu…</p>}
      {data && (data.content.length ? <div className="table-wrap"><table><thead><tr><th>Mã bệnh nhân</th><th>Trạng thái yêu cầu</th></tr></thead><tbody>{data.content.map(g => <tr key={g.id}><td>{g.patientCode}</td><td>{status(g)}</td></tr>)}</tbody></table></div> : <p>Chưa có yêu cầu cấp quyền.</p>)}
      {data && <div className="pagination"><button className="secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Trước</button><span>Trang {page + 1}</span><button className="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Sau</button></div>}</>}
  </section>;
}
