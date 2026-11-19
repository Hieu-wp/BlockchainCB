import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from './api';
function useRead(loader, deps) {
  const [state, setState] = useState({ loading: true, data: null, error: '' });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    // Clear prior medical data before rechecking current consent with the backend.
    setState({ loading: true, data: null, error: '' });
    loader(abort.signal)
      .then(data => {
        if (!abort.signal.aborted) setState({ loading: false, data, error: '' });
      })
      .catch(error => {
        if (!abort.signal.aborted && error.name !== 'AbortError') {
          setState({ loading: false, data: null, error: error.message });
        }
      });
    const reload = () => { if (!document.hidden) setTick(t => t + 1); };
    const timer = setInterval(reload, 30000);
    window.addEventListener('focus', reload);
    return () => {
      abort.abort();
      clearInterval(timer);
      window.removeEventListener('focus', reload);
    };
  }, [...deps, tick]);
  return { ...state, reload: () => setTick(t => t + 1) };
}
// Public timestamps have no zone; do not silently reinterpret them as UTC.
const date=value=>value?value.replace('T',' '):'—';
function State({state,children}) {
  if(state.loading)return <p role="status">Đang tải hồ sơ chung…</p>;
  if(state.error)return <><p className="alert error" role="alert">{state.error}</p><button className="secondary" onClick={state.reload}>Thử lại</button></>;
  return children(state.data);
}
function Pages({data,page,setPage}){return <div className="pagination"><span>{data.totalElements} kết quả · Trang {page+1}</span><button className="secondary" disabled={!page} onClick={()=>setPage(p=>p-1)}>Trước</button><button className="secondary" disabled={page+1>=data.totalPages} onClick={()=>setPage(p=>p+1)}>Sau</button></div>;}
export function SharedRecordTable({data}) {
  return !data.content?.length?<p className="empty">Chưa có hồ sơ chung phù hợp và còn quyền đọc.</p>:<div className="table-wrap"><table><thead><tr><th>Hồ sơ</th><th>Bệnh nhân</th><th>Ngày khám</th><th>Phiên bản</th><th>Trạng thái lần khám</th></tr></thead><tbody>{data.content.map(r=><tr key={r.id}><td><Link to={`/shared-records/${r.id}`}>{r.recordCode}</Link></td><td>{r.patientCode}</td><td>{date(r.visitAt)}</td><td>v{r.currentVersion}</td><td>{({COMPLETED:'Hoàn tất',PENDING_RESULT:'Chờ kết quả',CANCELLED:'Đã hủy'})[r.status]||r.status}</td></tr>)}</tbody></table></div>;
}
export function SharedRecordHistory({patient=false}) {
  const {patientId}=useParams();const [code,setCode]=useState(''),[filter,setFilter]=useState(''),[page,setPage]=useState(0);
  const state=useRead(s=>patient?api.sharedPatientRecords(patientId,page,s):api.sharedRecords(filter,page,s),[patient,patientId,filter,page]);
  return <><div className="page-title"><div><span className="eyebrow">HỒ SƠ CHUNG</span><h1>{patient?'Bệnh án bệnh nhân được cấp quyền':'Lịch sử hồ sơ'}</h1><p>{patient?'Quyền đọc được kiểm tra ở từng lần truy cập.':'Hồ sơ do bạn tạo trong nguồn dữ liệu chung và vẫn còn quyền đọc.'}</p></div></div>
    <section className="panel"><p className="muted">Hiện hỗ trợ xem hồ sơ và phiên bản. Tạo/cập nhật, tải tệp và xác minh Blockchain chung còn chờ thống nhất tích hợp.</p>
      {!patient&&<form className="search" onSubmit={e=>{e.preventDefault();setFilter(code.trim());setPage(0);}}><label>Mã bệnh nhân<input aria-label="Lọc mã bệnh nhân chung" value={code} maxLength={128} onChange={e=>setCode(e.target.value)}/></label><button>Lọc</button></form>}
      <State state={state}>{data=><><SharedRecordTable data={data}/><Pages data={data} page={page} setPage={setPage}/></>}</State>
    </section></>;
}
export function SharedRecordDetail() {
  const {id}=useParams();const [number,setNumber]=useState(null),[page,setPage]=useState(0);
  const state=useRead(async signal=>{const detail=await api.sharedRecord(id,signal);const versions=await api.sharedVersions(id,page,signal);const v=number?await api.sharedVersion(id,number,signal):detail.version;return {r:detail.record,v,versions};},[id,number,page]);
  return <><Link to="/records">← Lịch sử hồ sơ</Link><h1>Chi tiết bệnh án chung</h1><State state={state}>{({r,v,versions})=><>
    <section className="panel"><h2>{r.recordCode} · {r.patientCode}</h2><p>{r.patientName} · {r.specialty} · {date(r.visitAt)}</p><p>Lý do khám: {r.reason}</p><p>Phiên bản {v.versionNumber} · Tài khoản tạo #{v.createdByUserId} · {date(v.createdAt)}</p></section>
    <div className="detail-grid"><section className="panel medical-content">{[['Chẩn đoán',v.diagnosis],['Chẩn đoán phụ',v.secondaryDiagnosis],['Hướng điều trị',v.treatmentDirection],['Đơn thuốc dạng văn bản',v.prescription],['Ghi chú',v.note],['Phòng khám',v.roomName],['Hình thức khám',v.visitType],['Lịch tái khám',v.nextVisitDate],['Lưu ý tái khám',v.nextVisitNote]].map(([label,text])=><section key={label}><h3>{label}</h3><p className="medical-text">{text||'Chưa có thông tin'}</p></section>)}
      <h3>Thuốc theo phiên bản</h3>{v.medicines.length?<div className="table-wrap"><table><thead><tr><th>Thuốc</th><th>Hoạt chất</th><th>Cách dùng</th><th>Số lượng</th></tr></thead><tbody>{v.medicines.map((m,i)=><tr key={i}><td>{m.medicineName}</td><td>{m.activeIngredient||'—'}</td><td>{m.dosageInstruction||'—'}</td><td>{m.quantityText}</td></tr>)}</tbody></table></div>:<p>Chưa có thuốc dạng danh sách.</p>}
      <h3>Chỉ số sức khỏe</h3>{v.vitals?<dl>{[['Huyết áp',v.vitals.bloodPressure],['Nhịp tim',v.vitals.heartRate],['Nhiệt độ',v.vitals.temperature],['SpO₂',v.vitals.spo2],['Cân nặng',v.vitals.weight],['Chiều cao',v.vitals.height],['BMI',v.vitals.bmi]].map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value??'—'}</dd></div>)}</dl>:<p>Chưa có chỉ số.</p>}
      <h3>Tệp đính kèm</h3>{v.attachments.length?<ul>{v.attachments.map(a=><li key={a.id}>{a.fileName} · {a.fileType} {a.sizeLabel||''}</li>)}</ul>:<p>Không có tệp đính kèm.</p>}<small>Tải tệp riêng tư chưa tích hợp; không mở đường dẫn tệp trực tiếp.</small>
    </section><aside><section className="panel"><h2>Lịch sử phiên bản</h2>{versions.content.map(item=><button className="timeline-item" key={item.id} onClick={()=>setNumber(item.versionNumber)}>Phiên bản {item.versionNumber} · {date(item.createdAt)}</button>)}<Pages data={versions} page={page} setPage={setPage}/></section>
      <section className="panel hash-panel"><h2>Thông tin hash đã lưu</h2><code>{v.storedDataHash||'Chưa có hash'}</code><p>Trạng thái lưu trong database: {v.storedChainStatus||'NONE'}</p><p>Hash và trạng thái này chưa được Java xác minh theo quy tắc canonical chung. Chưa có kết luận toàn vẹn hoặc xác nhận Blockchain.</p>{v.storedTransactionHash&&<code>{v.storedTransactionHash}</code>}</section></aside></div>
    </>}</State></>;
}
