import { useEffect,useRef,useState } from 'react';
import { Link,useNavigate,useParams,useSearchParams } from 'react-router-dom';
import { api } from './api';

const localNow=()=>new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16);
const fields=[['diagnosis','Chẩn đoán chính',10000],['secondaryDiagnosis','Chẩn đoán phụ',10000],
  ['testResults','Kết quả xét nghiệm',10000],['treatmentDirection','Hướng điều trị',255],['note','Ghi chú',10000],['nextVisitNote','Hướng dẫn tái khám',10000]];
export default function SharedClinicalForm(){
  const {id}=useParams();const [search]=useSearchParams();const navigate=useNavigate();
  const [loaded,setLoaded]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [attempt,setAttempt]=useState(0);
  const [medicines,setMedicines]=useState([]),[uploads,setUploads]=useState([]),[keep,setKeep]=useState([]);
  const lock=useRef(false);const patient=loaded?.record?.patientId||Number(search.get('patientId'));
  useEffect(()=>{const abort=new AbortController();setLoaded(null);setError('');
    (async()=>{let detail=null,proof=null;
      if(id){detail=await api.sharedRecord(id,abort.signal);proof=await api.sharedIntegrity(id,detail.record.currentVersion,abort.signal);}
      const access=await api.clinicalAccess(detail?.record.patientId||Number(search.get('patientId')),abort.signal);
      if(!abort.signal.aborted){setLoaded({...detail,proof,access});setMedicines((detail?.version.medicines||[]).map(m=>({medicineName:m.medicineName,activeIngredient:m.activeIngredient||'',dosageInstruction:m.dosageInstruction||'',quantityText:m.quantityText})));setKeep(detail?.version.attachments.map(f=>f.id)||[]);}
    })().catch(e=>{if(!abort.signal.aborted)setError(`${e.message}${e.status?` (HTTP ${e.status})`:''}`);});return()=>abort.abort();
  },[id,search,attempt]);
  async function upload(file){if(!file)return;if(file.size>5*1024*1024){setError('Tệp tối đa 5 MiB.');return;}
    setBusy(true);try{const result=await api.clinicalUpload(patient,file);setUploads(old=>[...old,{id:result.id,name:file.name}]);}
    catch(e){setError(e.message);}finally{setBusy(false);}}
  async function submit(event){event.preventDefault();if(lock.current)return;lock.current=true;setBusy(true);setError('');
    const values=Object.fromEntries(new FormData(event.currentTarget));
    const vitals={};for(const key of ['heartRate','temperature','spo2','weight','height','bmi'])vitals[key]=values[key]===''?null:Number(values[key]);vitals.bloodPressure=values.bloodPressure;
    const content={patientId:patient,visitAt:values.visitAt,reason:values.reason,diagnosis:values.diagnosis,
      secondaryDiagnosis:values.secondaryDiagnosis,testResults:values.testResults,treatmentDirection:values.treatmentDirection,note:values.note,
      visitType:values.visitType,roomName:values.roomName,nextVisitDate:values.nextVisitDate||null,nextVisitNote:values.nextVisitNote,
      medicines,vitals,uploadIds:uploads.map(f=>f.id),keepAttachmentIds:keep};
    try{const result=id?await api.clinicalUpdate(id,{expectedVersion:loaded.record.currentVersion,content}):await api.clinicalCreate(content);navigate(`/shared-records/${result.id}?anchor=${id?loaded.record.currentVersion+1:1}#blockchain-confirmation`);}
    catch(e){setError(e.message);}finally{lock.current=false;setBusy(false);}
  }
  const version=loaded?.version;const canSave=loaded?.access.canCreate && (!id||loaded.proof?.verified);
  return <><Link to="/patients">← Chọn bệnh nhân</Link><h1>{id?'Tạo phiên bản khám mới':'Tạo hồ sơ khám và kê đơn'}</h1>
    <p>1. Chọn bệnh nhân → 2. Nhập thông tin → 3. Lưu và xem hồ sơ</p>
    {error&&<p className="alert error" role="alert">{error}</p>}
    {!loaded&&error&&<button type="button" className="secondary" onClick={()=>setAttempt(n=>n+1)}>Kiểm tra lại quyền tạo hồ sơ</button>}
    {!loaded&&!error&&<p role="status">Đang kiểm tra quyền bệnh nhân…</p>}
    {loaded&&<form onSubmit={submit} className="record-form">
      {!canSave&&<p className="alert error" role="alert">{loaded.access.reason==='SHARED_WRITE_MIGRATION_REQUIRED'?'Chưa có migration metadata được nhóm phê duyệt. Form đã sẵn sàng nhưng chưa thể lưu trên database này.':'Phiên bản chưa có hash hợp lệ để cập nhật.'}</p>}
      <section className="panel"><h2>A. Thông tin lần khám</h2><p>Bệnh nhân: {loaded.record?.patientCode||loaded.access.patientCode||`#${patient}`} · {loaded.access.fullName||''}</p>
        <label>Ngày giờ khám<input name="visitAt" type="datetime-local" step="any" required readOnly={!!id} defaultValue={loaded.record?.visitAt||localNow()}/></label>
        <label>Lý do khám<input name="reason" required maxLength={500} readOnly={!!id} defaultValue={loaded.record?.reason||''}/></label>
        <label>Hình thức khám<input name="visitType" maxLength={100} defaultValue={version?.visitType||''}/></label>
        <label>Phòng khám<input name="roomName" maxLength={50} defaultValue={version?.roomName||''}/></label>
      </section>
      <section className="panel"><h2>B. Thông tin chuyên môn</h2>{fields.map(([key,label,max])=><label key={key}>{label}<textarea name={key} required={key==='diagnosis'} maxLength={max} defaultValue={key==='testResults'?loaded.proof?.testResults||'':version?.[key]||''}/></label>)}
        <label>Ngày tái khám<input type="date" name="nextVisitDate" defaultValue={version?.nextVisitDate||''}/></label>
      </section>
      <section className="panel"><h2>C. Chỉ số sức khỏe</h2><label>Huyết áp<input name="bloodPressure" maxLength={20} defaultValue={version?.vitals?.bloodPressure||''}/></label>
        {[['heartRate','Nhịp tim',1,300,1],['temperature','Nhiệt độ (°C)',25,45,0.1],['spo2','SpO2 (%)',0,100,1],['weight','Cân nặng (kg)',0.01,999.99,0.01],['height','Chiều cao (cm)',0.1,300,0.1],['bmi','BMI',0.1,100,0.1]].map(([key,label,min,max,step])=><label key={key}>{label}<input name={key} type="number" min={min} max={max} step={step} defaultValue={version?.vitals?.[key]??''}/></label>)}
      </section>
      <section className="panel"><h2>D. Kê đơn thuốc</h2><p>Bác sĩ tự nhập thuốc và hướng dẫn liều dùng.</p>
        {medicines.map((medicine,index)=><fieldset key={index}><legend>Thuốc {index+1}</legend>
          {[['medicineName','Tên thuốc',150],['activeIngredient','Hoạt chất',150],['dosageInstruction','Hướng dẫn liều dùng',255],['quantityText','Số lượng và đơn vị',50]].map(([key,label,max])=><label key={key}>{label}<input required={['medicineName','quantityText'].includes(key)} maxLength={max} value={medicine[key]||''} onChange={event=>setMedicines(old=>old.map((m,i)=>i===index?{...m,[key]:event.target.value}:m))}/></label>)}
          <button type="button" className="secondary" onClick={()=>setMedicines(old=>old.filter((_,i)=>i!==index))}>Xóa thuốc {index+1}</button>
        </fieldset>)}
        <button type="button" disabled={medicines.length>=100} onClick={()=>setMedicines(old=>[...old,{medicineName:'',activeIngredient:'',dosageInstruction:'',quantityText:''}])}>Thêm thuốc</button>
      </section>
      <section className="panel"><h2>E. Tệp đính kèm</h2><p>PDF, PNG, JPEG tối đa 5 MiB; tối đa 10 tệp mỗi phiên bản.</p>
        {version?.attachments.map(f=><label key={f.id}><input type="checkbox" checked={keep.includes(f.id)} onChange={e=>setKeep(old=>e.target.checked?[...old,f.id]:old.filter(n=>n!==f.id))}/>Giữ {f.fileName} trong phiên bản mới</label>)}
        <input aria-label="Tệp đính kèm" type="file" accept="application/pdf,image/png,image/jpeg" disabled={busy||!canSave||uploads.length+keep.length>=10} onChange={e=>{upload(e.target.files[0]);e.target.value='';}}/>
        {uploads.map(f=><p key={f.id}>{f.name} <button type="button" onClick={()=>setUploads(old=>old.filter(x=>x.id!==f.id))}>Bỏ tệp</button></p>)}
      </section><button disabled={busy||!canSave}>{busy?'Đang xử lý…':id?'Lưu phiên bản mới':'Lưu hồ sơ khám'}</button>
    </form>}</>;
}
