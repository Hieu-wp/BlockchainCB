package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.blockchain.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared public versions. Never mixes legacy record IDs with public record IDs. */
@Service
@Transactional
public class SharedAnchorService {
  private final JdbcTemplate db;
  private final ClinicalService clinical;
  private final SharedRecordRepository records;
  private final DoctorIdentityService identities;
  private final WalletLinkService wallets;
  private final AnchorGateway chain;
  private final ChainSettings settings;
  public SharedAnchorService(JdbcTemplate db,ClinicalService clinical,SharedRecordRepository records,
      DoctorIdentityService identities,WalletLinkService wallets,AnchorGateway chain,ChainSettings settings){
    this.db=db;this.clinical=clinical;this.records=records;this.identities=identities;
    this.wallets=wallets;this.chain=chain;this.settings=settings;
  }
  private void schema(){
    db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{
      var m=c.getMetaData();boolean h2="H2".equals(m.getDatabaseProductName());
      for(String name:List.of("shared_record_anchors","shared_anchor_intents")){
        try(var r=m.getTables(null,h2?"DOCTOR_BACKEND":"doctor_backend",h2?name.toUpperCase(Locale.ROOT):name,new String[]{"TABLE"})){
          if(!r.next())throw new ApiException(503,"SHARED_ANCHOR_MIGRATION_REQUIRED");
        }
      }return null;
    });
  }
  private Map<String,Object> guard(DoctorPrincipal actor,long id,int number,boolean author){
    identities.approved(actor);
    var proof=clinical.proof(actor,id,number); // current consent and recomputed canonical hash
    if(!Boolean.TRUE.equals(proof.get("verified")))throw new ApiException(409,"CONTENT_INTEGRITY_FAILED");
    var root=records.allowed(id,actor.doctorId());
    db.queryForList("SELECT id FROM public.medical_records WHERE id=? FOR UPDATE",id);
    var version=records.version(id,number);
    if(author && version.createdByUserId()!=actor.userId())throw new ApiException(403,"ONLY_VERSION_AUTHOR_CAN_ANCHOR");
    schema();return proof;
  }
  private Map<String,Object> version(long id,int n){
    var list=db.queryForList("SELECT id,chain_status,tx_hash FROM public.record_versions WHERE record_id=? AND version_no=?",id,n);
    if(list.size()!=1)throw new ApiException(409,"SHARED_VERSION_MISSING");return list.get(0);
  }
  private AnchorGateway.Payload payload(DoctorPrincipal actor,long id,int n,Map<String,Object> proof){
    var key=db.queryForMap("SELECT * FROM doctor_backend.shared_record_anchors WHERE record_id=?",id);
    var v=version(id,n);var intent=intent(((Number)v.get("id")).longValue());
    if(!intent.get("content_hash").equals("0x"+proof.get("hash")) ||
       !intent.get("previous_hash").equals(n==1?ChainSettings.ZERO:"0x"+proof.get("previousHash")))
      throw new ApiException(409,"CONTENT_INTEGRITY_FAILED");
    return new AnchorGateway.Payload((String)key.get("record_key"),(String)key.get("patient_wallet"),n,
      (String)intent.get("content_hash"),(String)intent.get("previous_hash"),ChainSettings.CONTRACT,ChainSettings.CHAIN,(String)intent.get("doctor_wallet"));
  }
  private Map<String,Object> intent(long vid){return db.queryForMap("SELECT * FROM doctor_backend.shared_anchor_intents WHERE version_id=?",vid);}
  public AnchorService.Prepared prepare(DoctorPrincipal actor,long id,int n){
    settings.requireEnabled();var proof=guard(actor,id,n,true);var v=version(id,n);long vid=((Number)v.get("id")).longValue();
    var doctor=identities.approved(actor);
    if(doctor.getUser().getWalletAddress()==null)throw new ApiException(409,"DOCTOR_WALLET_MISSING");
    String wallet=wallets.requiredWallet(actor.userId(),doctor.getUser().getWalletAddress());
    var root=records.allowed(id,actor.doctorId());
    var patient=db.queryForMap("SELECT u.id,u.wallet_address,u.role,u.enabled FROM public.patients p JOIN public.users u ON u.id=p.user_id WHERE p.id=?",root.patientId());
    if(!"PATIENT".equals(patient.get("role")) || !Boolean.TRUE.equals(patient.get("enabled")))throw new ApiException(403,"PATIENT_READ_PERMISSION_REQUIRED");
    if(patient.get("wallet_address")==null)throw new ApiException(409,"PATIENT_WALLET_MISSING");
    // Patient module must supply verifiable ownership, not merely a client-supplied address.
    String patientWallet=wallets.requiredWallet(((Number)patient.get("id")).longValue(),(String)patient.get("wallet_address"));
    if(!chain.doctorRole(wallet))throw new ApiException(403,"DOCTOR_ROLE_MISSING");
    if(!chain.writePermission(patientWallet,wallet))throw new ApiException(403,"PATIENT_WRITE_PERMISSION_MISSING");
    var keys=db.queryForList("SELECT * FROM doctor_backend.shared_record_anchors WHERE record_id=?",id);
    if(keys.isEmpty())db.update("INSERT INTO doctor_backend.shared_record_anchors(record_id,record_key,patient_wallet) VALUES (?,?,?)",id,AnchorService.opaqueKey(),patientWallet);
    else if(!patientWallet.equalsIgnoreCase((String)keys.get(0).get("patient_wallet")))throw new ApiException(409,"PATIENT_WALLET_CHANGED_REQUIRES_REVIEW");
    var old=db.queryForList("SELECT * FROM doctor_backend.shared_anchor_intents WHERE version_id=?",vid);
    if(!old.isEmpty()){
      if(!wallet.equalsIgnoreCase((String)old.get(0).get("doctor_wallet")))throw new ApiException(409,"ANCHOR_INTENT_CHANGED");
      if(!"PREPARED".equals(old.get(0).get("status")))throw new ApiException(409,"ANCHOR_EXISTS_CHECK_STATUS");
    }
    String key=(String)db.queryForMap("SELECT record_key FROM doctor_backend.shared_record_anchors WHERE record_id=?",id).get("record_key");
    String previous=n==1?ChainSettings.ZERO:ChainSettings.hex32("0x"+proof.get("previousHash"));
    var state=chain.state(key);
    if(state.latestVersion()!=n-1)throw new ApiException(409,"ONCHAIN_VERSION_ORDER_MISMATCH");
    if(n>1 && (!patientWallet.equalsIgnoreCase(state.patientWallet()) || !previous.equalsIgnoreCase(state.latestHash()) || !previous.equalsIgnoreCase(chain.versionHash(key,n-1))))throw new ApiException(409,"ONCHAIN_PREVIOUS_HASH_MISMATCH");
    if(old.isEmpty())db.update("INSERT INTO doctor_backend.shared_anchor_intents(version_id,doctor_wallet,content_hash,previous_hash,scan_from,status,code) VALUES (?,?,?,?,?,'PREPARED','AWAITING_WALLET')",vid,wallet,ChainSettings.hex32("0x"+proof.get("hash")),previous,chain.head());
    var p=payload(actor,id,n,proof);
    return new AnchorService.Prepared(p.recordKey(),p.patientWallet(),n,p.contentHash(),p.previousHash(),p.contractAddress(),p.chainId(),p.doctorWallet(),Long.toString(vid));
  }
  public Map<String,Object> submit(DoctorPrincipal actor,long id,int n,String hash){
    settings.requireEnabled();var proof=guard(actor,id,n,true);hash=ChainSettings.hex32(hash);
    long vid=((Number)version(id,n).get("id")).longValue();
    var rows=db.queryForList("SELECT * FROM doctor_backend.shared_anchor_intents WHERE version_id=?",vid);
    if(rows.isEmpty())throw new ApiException(409,"PREPARE_ANCHOR_FIRST");
    var a=rows.get(0);var p=payload(actor,id,n,proof);
    String wallet=wallets.requiredWallet(actor.userId(),identities.approved(actor).getUser().getWalletAddress());
    if(!wallet.equalsIgnoreCase(p.doctorWallet()))throw new ApiException(409,"ANCHOR_INTENT_CHANGED");
    if(a.get("transaction_hash")!=null && !hash.equals(a.get("transaction_hash")))throw new ApiException(409,"TRANSACTION_ALREADY_REGISTERED");
    if("CONFIRMED".equals(a.get("status")) && hash.equals(a.get("transaction_hash")))return response(vid);
    if(!db.queryForList("SELECT version_id FROM doctor_backend.shared_anchor_intents WHERE transaction_hash=? AND version_id<>?",hash,vid).isEmpty())throw new ApiException(409,"TRANSACTION_ALREADY_USED");
    // Reject a foreign/mismatched tx before binding it, preventing malicious poisoning.
    AnchorGateway.Verified result;
    try{result=chain.verify(p,hash);}catch(ApiException e){if(e.getStatus()!=503)throw e;result=new AnchorGateway.Verified("PENDING","RPC_UNAVAILABLE",null,null);}
    db.update("UPDATE doctor_backend.shared_anchor_intents SET transaction_hash=? WHERE version_id=?",hash,vid);
    apply(actor,vid,hash,result);return response(vid);
  }
  public Map<String,Object> status(DoctorPrincipal actor,long id,int n){
    var proof=guard(actor,id,n,false);long vid=((Number)version(id,n).get("id")).longValue();
    var rows=db.queryForList("SELECT * FROM doctor_backend.shared_anchor_intents WHERE version_id=?",vid);
    if(rows.isEmpty())return Map.of("status","NONE","enabled",enabled(),"chainId",ChainSettings.CHAIN,"contractAddress",ChainSettings.CONTRACT);
    var a=rows.get(0);
    if(enabled()){
      var p=payload(actor,id,n,proof);String hash=(String)a.get("transaction_hash");
      try{
        if(hash==null){var scan=chain.scan(p,((Number)a.get("scan_from")).longValue());hash=scan.transactionHash();db.update("UPDATE doctor_backend.shared_anchor_intents SET scan_from=? WHERE version_id=?",scan.nextBlock(),vid);}
        if(hash!=null){
          var verified=chain.verify(p,hash);
          db.update("UPDATE doctor_backend.shared_anchor_intents SET transaction_hash=? WHERE version_id=?",hash,vid);
          apply(actor,vid,hash,verified);
        }
      }catch(ApiException e){
        if(e.getStatus()==503)db.update("UPDATE doctor_backend.shared_anchor_intents SET code='RPC_UNAVAILABLE' WHERE version_id=?",vid);
        else if(a.get("transaction_hash")!=null)apply(actor,vid,(String)a.get("transaction_hash"),new AnchorGateway.Verified("FAILED","VERIFICATION_MISMATCH",null,null));
        else throw e;
      }
    }
    return response(vid);
  }
  private boolean enabled(){return settings.enabled && settings.reviewed && !settings.rpc.isBlank();}
  private void apply(DoctorPrincipal actor,long vid,String hash,AnchorGateway.Verified r){
    if(!Set.of("PENDING","CONFIRMED","FAILED").contains(r.status()))throw new ApiException(503,"RPC_RESPONSE_INVALID");
    db.update("UPDATE doctor_backend.shared_anchor_intents SET status=?,code=?,block_number=?,block_hash=? WHERE version_id=?",r.status(),r.code(),r.blockNumber(),r.blockHash(),vid);
    db.update("UPDATE public.record_versions SET tx_hash=?,chain_status=? WHERE id=?",hash,r.status(),vid);
    db.update("INSERT INTO doctor_backend.audit_logs(actor_user_id,action,resource,outcome,occurred_at) VALUES (?,?,?,?,?)",actor.userId(),"SHARED_ANCHOR_VERIFY","public-version:"+vid,r.status(),java.time.Instant.now());
  }
  private Map<String,Object> response(long vid){
    var a=intent(vid);var out=new HashMap<String,Object>();
    out.put("status",a.get("status"));out.put("code",a.get("code"));out.put("transactionHash",a.get("transaction_hash"));
    out.put("blockNumber",a.get("block_number"));out.put("doctorWallet",a.get("doctor_wallet"));out.put("enabled",enabled());
    out.put("preparedAt",a.get("prepared_at").toString());out.put("contractAddress",ChainSettings.CONTRACT);out.put("chainId",ChainSettings.CHAIN);return out;
  }
}
