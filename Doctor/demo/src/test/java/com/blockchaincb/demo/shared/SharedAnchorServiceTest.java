package com.blockchaincb.demo.shared;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.blockchaincb.demo.blockchain.*;
import com.blockchaincb.demo.dto.SharedRecordResponse.*;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
class SharedAnchorServiceTest {
  JdbcTemplate db; ClinicalService clinical;SharedRecordRepository records;DoctorIdentityService identities;
  WalletLinkService wallets;AnchorGateway chain;SharedAnchorService service;
  DoctorPrincipal actor=new DoctorPrincipal(1L,10L);
  static final String D="0x"+"1".repeat(40),P="0x"+"2".repeat(40),H="3".repeat(64),TX="0x"+"5".repeat(64);
  @BeforeEach void setup(){
    var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(ds);
    db.execute("CREATE SCHEMA doctor_backend");
    db.execute("CREATE TABLE doctor_backend.audit_logs(actor_user_id BIGINT,action VARCHAR(32),resource VARCHAR(200),outcome VARCHAR(32),occurred_at TIMESTAMP WITH TIME ZONE)");
    db.execute("CREATE TABLE public.medical_records(id BIGINT PRIMARY KEY)");
    db.execute("CREATE TABLE public.record_versions(id BIGINT PRIMARY KEY,record_id BIGINT,version_no INT,chain_status VARCHAR(16),tx_hash VARCHAR(66))");
    db.execute("CREATE TABLE public.users(id BIGINT PRIMARY KEY,wallet_address VARCHAR(42),role VARCHAR(16),enabled BOOLEAN)");
    db.execute("CREATE TABLE public.patients(id BIGINT PRIMARY KEY,user_id BIGINT)");
    new ResourceDatabasePopulator(new ClassPathResource("shared-anchor-schema.sql")).execute(ds);
    db.update("INSERT INTO public.medical_records VALUES (100)");db.update("INSERT INTO public.record_versions VALUES(101,100,1,'NONE',null)");
    db.update("INSERT INTO public.users VALUES(2,?,'PATIENT',true)",P);db.update("INSERT INTO public.patients VALUES(20,2)");
    clinical=mock(ClinicalService.class);records=mock(SharedRecordRepository.class);identities=mock(DoctorIdentityService.class);wallets=mock(WalletLinkService.class);chain=mock(AnchorGateway.class);
    service=new SharedAnchorService(db,clinical,records,identities,wallets,chain,new ChainSettings(true,true,"mock-rpc","localhost:5173",3));
    var doctor=new Doctor();var user=new User();user.setWalletAddress(D);doctor.setUser(user);when(identities.approved(actor)).thenReturn(doctor);
    when(clinical.proof(actor,100,1)).thenReturn(Map.of("verified",true,"hash",H,"previousHash",""));
    var root=mock(Summary.class);when(root.patientId()).thenReturn(20L);when(records.allowed(100,10)).thenReturn(root);
    var version=mock(Version.class);when(version.createdByUserId()).thenReturn(1L);when(records.version(100,1)).thenReturn(version);
    when(wallets.requiredWallet(1L,D)).thenReturn(D);when(wallets.requiredWallet(2L,P)).thenReturn(P);
    when(chain.doctorRole(D)).thenReturn(true);when(chain.writePermission(P,D)).thenReturn(true);
    when(chain.state(anyString())).thenReturn(new AnchorGateway.State(P,0,ChainSettings.ZERO));
    when(chain.verify(any(),eq(TX))).thenReturn(new AnchorGateway.Verified("PENDING","RECEIPT_PENDING",null,null));
  }
  @Test void preparesOpaqueStableKeyAndPreservesNoneUntilBroadcast(){
    var p=service.prepare(actor,100,1);assertEquals(ChainSettings.ZERO,p.previousHash());assertEquals("0x"+H,p.contentHash());
    assertEquals(p.recordKey(),service.prepare(actor,100,1).recordKey());assertEquals(66,p.recordKey().length());
    assertEquals("NONE",db.queryForObject("SELECT chain_status FROM public.record_versions WHERE id=101",String.class));
  }
  @Test void pendingThenConfirmedUpdatesPublicVersionIdempotently(){
    service.prepare(actor,100,1);assertEquals("PENDING",service.submit(actor,100,1,TX).get("status"));
    when(chain.verify(any(),eq(TX))).thenReturn(new AnchorGateway.Verified("CONFIRMED","VERIFIED",99L,"0x"+"9".repeat(64)));
    assertEquals("CONFIRMED",service.status(actor,100,1).get("status"));
    assertEquals("CONFIRMED",service.submit(actor,100,1,TX).get("status"));
    assertEquals(TX,db.queryForObject("SELECT tx_hash FROM public.record_versions WHERE id=101",String.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM doctor_backend.shared_anchor_intents",Integer.class));
  }
  @Test void rpcTimeoutRemainsPending(){service.prepare(actor,100,1);when(chain.verify(any(),eq(TX))).thenThrow(new ApiException(503,"RPC_UNAVAILABLE"));assertEquals("PENDING",service.submit(actor,100,1,TX).get("status"));}
  @Test void revertedReceiptIsFailed(){service.prepare(actor,100,1);when(chain.verify(any(),eq(TX))).thenReturn(new AnchorGateway.Verified("FAILED","TRANSACTION_REVERTED",99L,null));assertEquals("FAILED",service.submit(actor,100,1,TX).get("status"));}
  @Test void foreignTransactionCannotPoisonIntent(){service.prepare(actor,100,1);when(chain.verify(any(),eq(TX))).thenThrow(new ApiException(422,"TRANSACTION_DATA_MISMATCH"));assertThrows(ApiException.class,()->service.submit(actor,100,1,TX));assertNull(db.queryForObject("SELECT transaction_hash FROM doctor_backend.shared_anchor_intents",String.class));}
  @Test void rejectsInvalidHash(){service.prepare(actor,100,1);assertThrows(ApiException.class,()->service.submit(actor,100,1,"fake"));}
  @Test void noRoleCannotPrepare(){when(chain.doctorRole(D)).thenReturn(false);assertEquals("DOCTOR_ROLE_MISSING",assertThrows(ApiException.class,()->service.prepare(actor,100,1)).getCode());}
  @Test void noWritePermissionCannotPrepare(){when(chain.writePermission(P,D)).thenReturn(false);assertEquals("PATIENT_WRITE_PERMISSION_MISSING",assertThrows(ApiException.class,()->service.prepare(actor,100,1)).getCode());}
  @Test void noPatientProofCannotPrepare(){when(wallets.requiredWallet(2L,P)).thenThrow(new ApiException(409,"WALLET_PROOF_MISSING"));assertThrows(ApiException.class,()->service.prepare(actor,100,1));}
  @Test void revokedConsentCannotReadStatusOrPrepare(){when(clinical.proof(actor,100,1)).thenThrow(new ApiException(403,"PATIENT_READ_PERMISSION_REQUIRED"));assertThrows(ApiException.class,()->service.status(actor,100,1));assertThrows(ApiException.class,()->service.prepare(actor,100,1));verifyNoInteractions(chain);}
  @Test void tamperedSnapshotCannotPrepare(){when(clinical.proof(actor,100,1)).thenReturn(Map.of("verified",false));assertThrows(ApiException.class,()->service.prepare(actor,100,1));}
  @Test void wrongVersionOrderCannotPrepare(){when(chain.state(anyString())).thenReturn(new AnchorGateway.State(P,2,"0x"+H));assertEquals("ONCHAIN_VERSION_ORDER_MISMATCH",assertThrows(ApiException.class,()->service.prepare(actor,100,1)).getCode());}
  @Test void recoversBroadcastHashAfterBrowserCloses(){service.prepare(actor,100,1);when(chain.scan(any(),anyLong())).thenReturn(new AnchorGateway.Scan(TX,100));when(chain.verify(any(),eq(TX))).thenReturn(new AnchorGateway.Verified("CONFIRMED","VERIFIED",99L,null));assertEquals(TX,service.status(actor,100,1).get("transactionHash"));}
  @Test void secondVersionUsesSameKeyAndChecksActualPreviousOnChainHash(){
    var first=service.prepare(actor,100,1);
    db.update("INSERT INTO public.record_versions VALUES(102,100,2,'NONE',null)");
    when(clinical.proof(actor,100,2)).thenReturn(Map.of("verified",true,"hash","6".repeat(64),"previousHash",H));
    var version=mock(Version.class);when(version.createdByUserId()).thenReturn(1L);when(records.version(100,2)).thenReturn(version);
    when(chain.state(first.recordKey())).thenReturn(new AnchorGateway.State(P,1,"0x"+H));
    when(chain.versionHash(first.recordKey(),1)).thenReturn("0x"+H);
    var next=service.prepare(actor,100,2);assertEquals(first.recordKey(),next.recordKey());assertEquals("0x"+H,next.previousHash());
    when(chain.versionHash(first.recordKey(),1)).thenReturn(ChainSettings.ZERO);
    assertEquals("ONCHAIN_PREVIOUS_HASH_MISMATCH",assertThrows(ApiException.class,()->service.prepare(actor,100,2)).getCode());
  }
  @Test void missingPatientWalletAndWrongAuthorAreBlocked(){
    db.update("UPDATE public.users SET wallet_address=null WHERE id=2");
    assertEquals("PATIENT_WALLET_MISSING",assertThrows(ApiException.class,()->service.prepare(actor,100,1)).getCode());
    when(records.version(100,1).createdByUserId()).thenReturn(99L);
    assertEquals("ONLY_VERSION_AUTHOR_CAN_ANCHOR",assertThrows(ApiException.class,()->service.prepare(actor,100,1)).getCode());
  }
}
