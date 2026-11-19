package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.security.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.service.HashService;
import com.blockchaincb.demo.storage.PrivateStorage;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public clinical writer. Sidecar preserves canonical proof; never writes legacy medical tables. */
@Service
@Transactional
public class ClinicalService {
  private final JdbcTemplate db;
  private final SharedGrantRepository grants;
  private final SharedRecordRepository reader;
  private final SharedIntegrationSettings settings;
  private final DoctorIdentityService identities;
  private final HashService hash;
  private final PrivateStorage storage;
  private final MetadataMemo<Boolean> metadata=new MetadataMemo<>();
  @org.springframework.beans.factory.annotation.Value("${doctor.integration.schema-cache-ms:0}")
  private long schemaCacheMs;
  public ClinicalService(JdbcTemplate db,SharedGrantRepository grants,SharedRecordRepository reader,
      SharedIntegrationSettings settings,DoctorIdentityService identities,HashService hash,PrivateStorage storage){
    this.db=db;this.grants=grants;this.reader=reader;this.settings=settings;this.identities=identities;this.hash=hash;this.storage=storage;
  }
  public void ready(){
    settings.requireEnabled();reader.contract();
    metadata.get(schemaCacheMs,()->{checkSidecar();return true;});
  }
  private void checkSidecar(){
    // A failed SELECT against a missing table aborts PostgreSQL's surrounding transaction.
    // Inspect catalog metadata instead, so capability can still return Patient info safely.
    try {db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
      var meta=connection.getMetaData();boolean h2="H2".equals(meta.getDatabaseProductName());
      String schema=h2?"DOCTOR_BACKEND":"doctor_backend";
      var required=Map.of("shared_version_proofs",Set.of("version_id","canonical_content","previous_hash","test_results","content_hash"),
        "shared_uploads",Set.of("id","user_id","patient_id","storage_key","file_name","media_type","size_bytes","sha256"));
      for(var entry:required.entrySet()){
        String table=h2?entry.getKey().toUpperCase(Locale.ROOT):entry.getKey();var found=new HashSet<String>();
        try(var columns=meta.getColumns(null,schema,table,null)){
          while(columns.next())if(table.equalsIgnoreCase(columns.getString("TABLE_NAME")))
            found.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        if(!found.containsAll(entry.getValue()))throw new ApiException(503,"SHARED_WRITE_MIGRATION_REQUIRED");
      }
      return null;
    });}
    catch(org.springframework.dao.DataAccessException e){throw new ApiException(503,"DATABASE_UNAVAILABLE");}
  }
  public boolean available(){try{ready();return true;}catch(ApiException e){if("SHARED_WRITE_MIGRATION_REQUIRED".equals(e.getCode()))return false;throw e;}}
  private long guard(DoctorPrincipal actor,long patient){
    identities.approved(actor);ready();
    long doctor=grants.contract().doctorReferencesUser()?actor.userId():actor.doctorId();
    grants.requireReadLocked(patient,doctor);
    if(!grants.patientAccountValid(patient))throw new ApiException(403,"PATIENT_READ_PERMISSION_REQUIRED");
    return actor.doctorId();
  }
  private long insert(String sql,Object... args){
    var key=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,new String[]{"id"});
      for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);
    return key.getKey().longValue();
  }
  public Map<String,Object> capability(DoctorPrincipal actor,long patient){
    // Check consent even when migration is missing; do not disclose Patient demographics before permission.
    identities.approved(actor);settings.requireEnabled();var c=grants.contract();
    grants.requireReadLocked(patient,c.doctorReferencesUser()?actor.userId():actor.doctorId());
    if(!grants.patientAccountValid(patient))throw new ApiException(403,"PATIENT_READ_PERMISSION_REQUIRED");
    boolean ready=true;try {ready();}catch(ApiException e){if(!"SHARED_WRITE_MIGRATION_REQUIRED".equals(e.getCode()))throw e;ready=false;}
    var patientInfo=db.queryForMap("SELECT p.patient_code,u.full_name FROM public.patients p JOIN public.users u ON u.id=p.user_id WHERE p.id=?",patient);
    return Map.of("canCreate",ready,"reason",ready?"ACTIVE_CONSENT":"SHARED_WRITE_MIGRATION_REQUIRED","patientCode",patientInfo.get("patient_code"),"fullName",patientInfo.get("full_name"));
  }
  public long create(DoctorPrincipal actor,ClinicalRequest request){
    guard(actor,request.patientId());
    if(!request.keepAttachmentIds().isEmpty())throw new ApiException(400,"INVALID_ATTACHMENT");
    long specialty=identities.approved(actor).getSpecialtyId();
    long id=insert("INSERT INTO public.medical_records(record_code,patient_id,doctor_id,specialty_id,visit_at,reason,status,current_version,created_at) VALUES (?,?,?,?,?,?,'COMPLETED',1,?)",
      "MR-"+UUID.randomUUID().toString().replace("-","").substring(0,26),request.patientId(),actor.doctorId(),specialty,request.visitAt(),request.reason(),LocalDateTime.now());
    append(actor,id,1,request,"");return id;
  }
  public long update(DoctorPrincipal actor,long id,ClinicalRequest.Update update){
    identities.approved(actor);settings.requireEnabled();reader.contract();
    var root=reader.allowed(id,actor.doctorId());guard(actor,root.patientId());
    var rows=db.queryForList("SELECT current_version,doctor_id FROM public.medical_records WHERE id=? FOR UPDATE",id);
    var r=rows.get(0);int current=((Number)r.get("current_version")).intValue();
    if(((Number)r.get("doctor_id")).longValue()!=actor.doctorId())throw new ApiException(403,"ONLY_RECORD_AUTHOR_CAN_UPDATE");
    if(current!=update.expectedVersion())throw new ApiException(409,"VERSION_CONFLICT");
    var q=update.content();
    if(q.patientId()!=root.patientId() || !q.visitAt().toString().equals(root.visitAt()) || !q.reason().equals(root.reason()))
      throw new ApiException(400,"VISIT_IDENTITY_IMMUTABLE");
    var proof=proof(actor,id,current);
    if(!Boolean.TRUE.equals(proof.get("verified")))throw new ApiException(409,"PUBLIC_INTEGRITY_UNVERIFIED");
    append(actor,id,current+1,q,(String)proof.get("hash"));
    db.update("UPDATE public.medical_records SET current_version=? WHERE id=?",current+1,id);return id;
  }
  private void append(DoctorPrincipal actor,long record,int number,ClinicalRequest q,String previous){
    String prescription=q.medicines().stream().map(m->m.medicineName()+" — "+Objects.toString(m.dosageInstruction(),"")+" — "+m.quantityText()).collect(java.util.stream.Collectors.joining("\n"));
    long version=insert("INSERT INTO public.record_versions(record_id,version_no,diagnosis,prescription,note,chain_status,created_by,created_at,room_name,visit_type,secondary_diagnosis,treatment_direction,next_visit_date,next_visit_note) VALUES (?,?,?,?,?,'NONE',?,?,?,?,?,?,?,?)",
      record,number,q.diagnosis(),prescription,q.note(),actor.userId(),LocalDateTime.now(),q.roomName(),q.visitType(),q.secondaryDiagnosis(),q.treatmentDirection(),q.nextVisitDate(),q.nextVisitNote());
    int order=0;
    for(var m:q.medicines())db.update("INSERT INTO public.prescription_items(record_version_id,sort_order,medicine_name,active_ingredient,dosage_instruction,quantity_text) VALUES (?,?,?,?,?,?)",
      version,++order,m.medicineName(),m.activeIngredient(),m.dosageInstruction(),m.quantityText());
    var v=q.vitals();if(v!=null)db.update("INSERT INTO public.vital_signs(record_version_id,blood_pressure,heart_rate,temperature,spo2,weight,height,bmi) VALUES (?,?,?,?,?,?,?,?)",
      version,v.bloodPressure(),v.heartRate(),v.temperature(),v.spo2(),v.weight(),v.height(),v.bmi());
    if(q.uploadIds().size()+q.keepAttachmentIds().size()>10)throw new ApiException(400,"TOO_MANY_FILES");
    if(new HashSet<>(q.uploadIds()).size()!=q.uploadIds().size() || new HashSet<>(q.keepAttachmentIds()).size()!=q.keepAttachmentIds().size())throw new ApiException(400,"DUPLICATE_FILES");
    for(Long file:q.keepAttachmentIds()){
      var old=db.queryForList("SELECT a.* FROM public.record_attachments a JOIN public.record_versions v ON v.id=a.record_version_id WHERE a.id=? AND v.record_id=? AND v.version_no<?",file,record,number);
      if(old.size()!=1)throw new ApiException(403,"INVALID_ATTACHMENT");var f=old.get(0);
      db.update("INSERT INTO public.record_attachments(record_version_id,file_name,file_type,file_size_label,file_url,uploaded_at) VALUES (?,?,?,?,?,?)",
        version,f.get("file_name"),f.get("file_type"),f.get("file_size_label"),f.get("file_url"),LocalDateTime.now());
    }
    for(Long upload:q.uploadIds()){
      var files=db.queryForList("SELECT * FROM doctor_backend.shared_uploads WHERE id=? AND user_id=? AND patient_id=?",upload,actor.userId(),q.patientId());
      if(files.size()!=1)throw new ApiException(403,"INVALID_ATTACHMENT");var f=files.get(0);
      if(!hash.sha256(storage.read((String)f.get("storage_key"))).equals(f.get("sha256")))throw new ApiException(409,"FILE_INTEGRITY_FAILED");
      db.update("INSERT INTO public.record_attachments(record_version_id,file_name,file_type,file_size_label,file_url,uploaded_at) VALUES (?,?,?,?,?,?)",
        version,f.get("file_name"),f.get("media_type"),f.get("size_bytes")+" bytes","doctor-private:"+f.get("storage_key"),LocalDateTime.now());
    }
    String tests=q.testResults()==null?"":q.testResults();String canonical=canonical(record,version,previous,tests);String digest=hash.sha256(canonical);
    db.update("INSERT INTO doctor_backend.shared_version_proofs(version_id,canonical_content,previous_hash,test_results,content_hash) VALUES (?,?,?,?,?)",version,canonical,previous,tests,digest);
    db.update("UPDATE public.record_versions SET data_hash=? WHERE id=?","0x"+digest,version);
  }
  private String rows(String sql,Object... args){
    var rows=db.query(sql,(r,n)->{
      var out=new StringBuilder();var meta=r.getMetaData();
      for(int i=1;i<=meta.getColumnCount();i++){
        Object x=r.getObject(i);
        if(x instanceof BigDecimal b)x=b.stripTrailingZeros().toPlainString();
        if(x instanceof Timestamp)x=r.getObject(i,LocalDateTime.class).toString();
        out.append(hash.canonical(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),x==null?"NULL":"VALUE",x));
      }return out.toString();},args);
    return hash.canonical(rows.toArray());
  }
  private String canonical(long record,long version,String previous,String tests){
    return hash.canonical("public-clinical-v1",previous,tests,
      rows("SELECT id,patient_id,doctor_id,specialty_id,visit_at,reason FROM public.medical_records WHERE id=?",record),
      rows("SELECT id,record_id,version_no,diagnosis,prescription,note,created_by,created_at,room_name,visit_type,secondary_diagnosis,treatment_direction,next_visit_date,next_visit_note FROM public.record_versions WHERE id=?",version),
      rows("SELECT id,sort_order,medicine_name,active_ingredient,dosage_instruction,quantity_text FROM public.prescription_items WHERE record_version_id=? ORDER BY sort_order,id",version),
      rows("SELECT blood_pressure,heart_rate,temperature,spo2,weight,height,bmi FROM public.vital_signs WHERE record_version_id=?",version),
      rows("SELECT a.id,a.file_name,a.file_type,a.file_size_label,a.file_url,u.sha256 FROM public.record_attachments a LEFT JOIN doctor_backend.shared_uploads u ON a.file_url=concat('doctor-private:',u.storage_key) WHERE a.record_version_id=? ORDER BY a.id",version));
  }
  public Map<String,Object> proof(DoctorPrincipal actor,long record,int number){
    identities.approved(actor);settings.requireEnabled();reader.contract();
    var root=reader.allowed(record,actor.doctorId());guard(actor,root.patientId());
    var proofs=db.queryForList("SELECT p.*,v.data_hash FROM doctor_backend.shared_version_proofs p JOIN public.record_versions v ON v.id=p.version_id WHERE v.record_id=? AND v.version_no=?",record,number);
    if(proofs.size()!=1)return Map.of("verified",false,"status","UNVERIFIED_PUBLIC_CANONICAL");
    var p=proofs.get(0);String snapshot=(String)p.get("canonical_content"),digest=(String)p.get("content_hash");
    boolean valid=digest.equals(hash.sha256(snapshot)) && ("0x"+digest).equals(p.get("data_hash"))
      && snapshot.equals(canonical(record,((Number)p.get("version_id")).longValue(),(String)p.get("previous_hash"),(String)p.get("test_results")));
    if(number==1)valid &= "".equals(p.get("previous_hash"));
    else {
      var before=db.queryForList("SELECT p.content_hash,p.canonical_content,v.data_hash FROM doctor_backend.shared_version_proofs p JOIN public.record_versions v ON v.id=p.version_id WHERE v.record_id=? AND v.version_no=?",record,number-1);
      valid &= before.size()==1 && p.get("previous_hash").equals(before.get(0).get("content_hash"))
        && hash.sha256((String)before.get(0).get("canonical_content")).equals(before.get(0).get("content_hash"))
        && ("0x"+before.get(0).get("content_hash")).equals(before.get(0).get("data_hash"));
    }
    return Map.of("verified",valid,"hash",digest,"previousHash",p.get("previous_hash"),"testResults",p.get("test_results"),"status","NONE");
  }
  public long upload(DoctorPrincipal actor,long patient,String name,byte[] bytes){
    guard(actor,patient);if(bytes.length==0||bytes.length>5*1024*1024)throw new ApiException(400,"INVALID_FILE_SIZE");
    String type;
    if(bytes.length>=5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
    else if(bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))type="image/png";
    else if(bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255)type="image/jpeg";
    else throw new ApiException(400,"UNSUPPORTED_FILE");
    String safe=name==null?"attachment":name.replaceAll("[\\\\/\\r\\n]","_");if(safe.length()>200)safe=safe.substring(0,200);
    return insert("INSERT INTO doctor_backend.shared_uploads(user_id,patient_id,storage_key,file_name,media_type,size_bytes,sha256) VALUES (?,?,?,?,?,?,?)",
      actor.userId(),patient,storage.put(bytes),safe,type,bytes.length,hash.sha256(bytes));
  }
  public record Download(byte[] bytes,String type){}
  public Download download(DoctorPrincipal actor,long record,int number,long attachment){
    identities.approved(actor);settings.requireEnabled();reader.contract();
    var root=reader.allowed(record,actor.doctorId());guard(actor,root.patientId());
    var f=db.queryForList("SELECT u.* FROM public.record_attachments a JOIN public.record_versions v ON v.id=a.record_version_id JOIN doctor_backend.shared_uploads u ON a.file_url=concat('doctor-private:',u.storage_key) WHERE a.id=? AND v.record_id=? AND v.version_no=? AND u.patient_id=?",attachment,record,number,root.patientId());
    if(f.size()!=1)throw new ApiException(404,"PRIVATE_FILE_UNAVAILABLE");var file=f.get(0);byte[] bytes=storage.read((String)file.get("storage_key"));
    if(!hash.sha256(bytes).equals(file.get("sha256")))throw new ApiException(409,"FILE_INTEGRITY_FAILED");return new Download(bytes,(String)file.get("media_type"));
  }
}
