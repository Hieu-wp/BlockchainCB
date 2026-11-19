package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.blockchaincb.demo.security.DoctorPrincipal;
import com.blockchaincb.demo.shared.*;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(SharedGrantIntegrationTests.IsolatedDatabase.class)
class SharedGrantIntegrationTests {
  @org.springframework.boot.test.context.TestConfiguration
  static class IsolatedDatabase {
    @org.springframework.context.annotation.Bean
    javax.sql.DataSource dataSource() {
      var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
      ds.setDriverClassName("org.h2.Driver");
      ds.setUrl(
          "jdbc:h2:mem:shared_grants_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
      ds.setUsername("sa");
      ds.setPassword("");
      return ds;
    }
  }

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:shared_grants_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    r.add(
        "spring.sql.init.schema-locations",
        () ->
            "classpath:db/local-shared-schema.sql,classpath:db/local-doctor-schema.sql,classpath:db/local-blockchain-schema.sql,classpath:shared-grants-contract.sql,classpath:shared-clinical-read-contract.sql,classpath:shared-clinical-proof.sql");
    r.add("jwt.secret", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("doctor.integration.shared-grants-enabled", () -> true);
    r.add("doctor.blockchain.enabled", () -> false);
    r.add("doctor.blockchain.deployment-reviewed", () -> false);
    r.add("doctor.blockchain.rpc-url", () -> "");
    r.add("doctor.storage.local-enabled",()->true);
    r.add("doctor.storage.local-root",()->"./target/shared-private-files");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired SharedGrantService service;
  @Autowired ClinicalService clinical;
  @Autowired SharedGrantRepository grantMetadata;
  @Autowired SharedRecordRepository recordMetadata;

  @Test void schemaMetadataCacheNeverCachesPatientConsent()throws Exception{
    long p=patient("PC-CACHED-METADATA");var a=doctor("cached-metadata@test.local",true);activate(send(a,"PC-CACHED-METADATA"));
    org.springframework.test.util.ReflectionTestUtils.setField(grantMetadata,"schemaCacheMs",60000L);
    org.springframework.test.util.ReflectionTestUtils.setField(recordMetadata,"schemaCacheMs",60000L);
    try{
      long record=createClinical(a,p);
      mvc.perform(get("/api/doctor/shared-records/"+record).header("Authorization","Bearer "+a.token())).andExpect(status().isOk());
      db.update("UPDATE public.access_grants SET revoked=true WHERE patient_id=? AND doctor_id=?",p,a.doctor());
      mvc.perform(get("/api/doctor/shared-records/"+record).header("Authorization","Bearer "+a.token())).andExpect(status().isForbidden());
      mvc.perform(get("/api/doctor/patients").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }finally{
      org.springframework.test.util.ReflectionTestUtils.setField(grantMetadata,"schemaCacheMs",0L);
      org.springframework.test.util.ReflectionTestUtils.setField(recordMetadata,"schemaCacheMs",0L);
    }
  }

  String clinicalBody(long patient,String diagnosis){
    return """
      {"patientId":%d,"visitAt":"2026-01-01T10:30","reason":"Fixture visit","diagnosis":"%s",
       "testResults":"Fixture test result","note":"note","visitType":"Ngoai tru","roomName":"A1",
       "secondaryDiagnosis":"","treatmentDirection":"Fixture plan","nextVisitNote":"",
       "medicines":[{"medicineName":"Fixture A","activeIngredient":"A","dosageInstruction":"Doctor entered","quantityText":"2 tablets"},
                    {"medicineName":"Fixture B","activeIngredient":"B","dosageInstruction":"Doctor entered","quantityText":"1 bottle"}],
       "vitals":{"bloodPressure":"120/80","heartRate":70,"temperature":36.5,"spo2":98,"weight":60,"height":170,"bmi":20.8},
       "uploadIds":[],"keepAttachmentIds":[]}
      """.formatted(patient,diagnosis);
  }
  @Test void missingClinicalMigrationFailsClosedWithoutCreatingPublicRows()throws Exception{
    long p=patient("PC-MIGRATION");var a=doctor("migration-clinical@test.local",true);activate(send(a,"PC-MIGRATION"));
    db.execute("ALTER TABLE doctor_backend.shared_version_proofs RENAME TO shared_version_proofs_hidden");
    try{
      mvc.perform(get("/api/doctor/shared-patients/"+p+"/clinical-access").header("Authorization","Bearer "+a.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.canCreate").value(false))
        .andExpect(jsonPath("$.reason").value("SHARED_WRITE_MIGRATION_REQUIRED"))
        .andExpect(jsonPath("$.patientCode").value("PC-MIGRATION"));
      mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(clinicalBody(p,"Denied")))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SHARED_WRITE_MIGRATION_REQUIRED"));
      assertEquals(0,db.queryForObject("SELECT count(*) FROM public.medical_records",Integer.class));
    }finally{db.execute("ALTER TABLE doctor_backend.shared_version_proofs_hidden RENAME TO shared_version_proofs");}
  }
  @Test void concurrentClinicalUpdatesAppendOnlyOneExpectedVersion()throws Exception{
    long p=patient("PC-CONCURRENT-CLINICAL");var a=doctor("race-clinical@test.local",true);activate(send(a,"PC-CONCURRENT-CLINICAL"));long record=createClinical(a,p);
    var content=tools.jackson.databind.json.JsonMapper.builder().build().readValue(clinicalBody(p,"Updated"),ClinicalRequest.class);
    var update=new ClinicalRequest.Update(1,content);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
    try{
      Callable<Boolean> call=()->{start.await();try{clinical.update(a.principal(),record,update);return true;}catch(com.blockchaincb.demo.exception.ApiException e){if(e.getStatus()!=409)throw e;return false;}};
      var one=pool.submit(call);var two=pool.submit(call);start.countDown();assertNotEquals(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS));
      assertEquals(2,db.queryForObject("SELECT count(*) FROM public.record_versions WHERE record_id=?",Integer.class,record));
    }finally{pool.shutdownNow();}
  }
  long createClinical(Actor actor,long patient)throws Exception{
    var response=mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+actor.token())
        .contentType(MediaType.APPLICATION_JSON).content(clinicalBody(patient,"Initial")))
        .andExpect(status().isCreated()).andReturn();
    return ((Number)JsonPath.read(response.getResponse().getContentAsString(),"$.id")).longValue();
  }
  @Test void clinicalWriteRequiresApprovalAndActiveConsent()throws Exception{
    long p=patient("PC-WRITE");var a=doctor("write@test.local",true);long grant=send(a,"PC-WRITE");
    mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(clinicalBody(p,"Denied"))).andExpect(status().isForbidden());
    activate(grant);createClinical(a,p);db.update("UPDATE public.access_grants SET revoked=true WHERE id=?",grant);
    mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(clinicalBody(p,"Denied"))).andExpect(status().isForbidden());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM public.medical_records",Integer.class));
  }
  @Test void pendingCannotWriteAndPatientQuerySeesOwnSharedPrescription()throws Exception{
    long p=patient("PC-PATIENT-VIEW");var pending=doctor("pending-clinical@test.local",false);
    mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+pending.token()).contentType(MediaType.APPLICATION_JSON).content(clinicalBody(p,"Denied"))).andExpect(status().isForbidden());
    var a=doctor("patient-view@test.local",true);activate(send(a,"PC-PATIENT-VIEW"));createClinical(a,p);
    long user=db.queryForObject("SELECT user_id FROM public.patients WHERE id=?",Long.class,p);
    assertEquals(2,db.queryForObject("SELECT count(*) FROM public.prescription_items i JOIN public.record_versions v ON v.id=i.record_version_id JOIN public.medical_records r ON r.id=v.record_id JOIN public.patients p ON p.id=r.patient_id WHERE p.user_id=?",Integer.class,user));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM public.medical_records r JOIN public.patients p ON p.id=r.patient_id WHERE p.user_id=?",Integer.class,a.user()));
  }
  @Test void clinicalVersionsStoreOwnDrugsVitalsAndHashWithoutOverwriting()throws Exception{
    long p=patient("PC-RX");var a=doctor("rx@test.local",true);activate(send(a,"PC-RX"));long record=createClinical(a,p);
    var first=clinical.proof(a.principal(),record,1);assertEquals(true,first.get("verified"));
    String body="{\"expectedVersion\":1,\"content\":"+clinicalBody(p,"Updated")+"}";
    mvc.perform(post("/api/doctor/shared-records/"+record+"/versions").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    assertEquals(4,db.queryForObject("SELECT count(*) FROM public.prescription_items",Integer.class));
    assertEquals(2,db.queryForObject("SELECT count(*) FROM public.vital_signs",Integer.class));
    assertEquals(first.get("hash"),clinical.proof(a.principal(),record,1).get("hash"));
    assertEquals(true,clinical.proof(a.principal(),record,1).get("verified"));
    assertEquals(first.get("hash"),clinical.proof(a.principal(),record,2).get("previousHash"));
    assertEquals(true,clinical.proof(a.principal(),record,2).get("verified"));
    assertEquals(a.user(),db.queryForObject("SELECT created_by FROM public.record_versions WHERE record_id=? AND version_no=2",Long.class,record));
    mvc.perform(post("/api/doctor/shared-records/"+record+"/versions").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    db.update("UPDATE public.prescription_items SET quantity_text='tampered' WHERE record_version_id IN (SELECT id FROM public.record_versions WHERE record_id=? AND version_no=2)",record);
    assertEquals(false,clinical.proof(a.principal(),record,2).get("verified"));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM doctor_backend.medical_records",Integer.class));
  }
  @Test void invalidAttachmentRollsBackEntirePublicVisit()throws Exception{
    long p=patient("PC-ROLLBACK");var a=doctor("rollback-clinical@test.local",true);activate(send(a,"PC-ROLLBACK"));
    String body=clinicalBody(p,"Rollback").replace("\"uploadIds\":[]","\"uploadIds\":[999999]");
    mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    for(String table:List.of("medical_records","record_versions","prescription_items","vital_signs"))assertEquals(0,db.queryForObject("SELECT count(*) FROM public."+table,Integer.class));
  }
  @Test void filesArePrivateAndPreservedAcrossVersions()throws Exception{
    long p=patient("PC-FILE");var a=doctor("file-clinical@test.local",true);activate(send(a,"PC-FILE"));
    long upload=clinical.upload(a.principal(),p,"fixture.pdf","%PDF-fixture".getBytes());
    var response=mvc.perform(post("/api/doctor/shared-records").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
      .content(clinicalBody(p,"Files").replace("\"uploadIds\":[]","\"uploadIds\":["+upload+"]"))).andExpect(status().isCreated()).andReturn();
    long record=((Number)JsonPath.read(response.getResponse().getContentAsString(),"$.id")).longValue();
    long attachment=db.queryForObject("SELECT id FROM public.record_attachments",Long.class);
    String update=clinicalBody(p,"Updated").replace("\"keepAttachmentIds\":[]","\"keepAttachmentIds\":["+attachment+"]");
    mvc.perform(post("/api/doctor/shared-records/"+record+"/versions").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
      .content("{\"expectedVersion\":1,\"content\":"+update+"}")).andExpect(status().isCreated());
    assertEquals(2,db.queryForObject("SELECT count(*) FROM public.record_attachments",Integer.class));
    assertArrayEquals("%PDF-fixture".getBytes(),clinical.download(a.principal(),record,1,attachment).bytes());
    db.update("UPDATE public.access_grants SET revoked=true");
    assertThrows(com.blockchaincb.demo.exception.ApiException.class,()->clinical.download(a.principal(),record,1,attachment));
  }

  record Actor(long doctor, long user, String token) {
    DoctorPrincipal principal() {
      return new DoctorPrincipal(user, doctor);
    }
  }

  @BeforeEach
  void clean() {
    assertNotNull(db.queryForObject("SELECT H2VERSION()", String.class));
    for (String t :
        List.of(
            "doctor_backend.shared_version_proofs",
            "doctor_backend.shared_uploads",
            "public.record_attachments",
            "public.vital_signs",
            "public.prescription_items",
            "public.record_versions",
            "public.medical_records",
            "public.access_grants",
            "public.patients",
            "doctor_backend.anchor_attempts",
            "doctor_backend.record_anchors",
            "doctor_backend.wallet_challenges",
            "doctor_backend.wallet_bindings",
            "doctor_backend.audit_logs",
            "doctor_backend.version_attachments",
            "doctor_backend.record_versions",
            "doctor_backend.attachments",
            "doctor_backend.medical_records",
            "doctor_backend.access_grants",
            "doctor_backend.patient_links",
            "public.doctors",
            "public.users")) db.update("DELETE FROM " + t);
  }

  long patient(String code) {
    db.update(
        "INSERT INTO public.users(email,password_hash,full_name,role,enabled) VALUES (?,?,'Private"
            + " Patient Name','PATIENT',true)",
        code + "@test.local",
        "not-an-auth-fixture");
    long user =
        db.queryForObject(
            "SELECT id FROM public.users WHERE email=?", Long.class, code + "@test.local");
    db.update(
        "INSERT INTO public.patients(user_id,patient_code,date_of_birth,gender) VALUES (?,?,DATE"
            + " '1990-01-01','MALE')",
        user,
        code);
    return db.queryForObject(
        "SELECT id FROM public.patients WHERE patient_code=?", Long.class, code);
  }

  Actor doctor(String email, boolean approved) throws Exception {
    mvc.perform(
            post("/api/auth/doctor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"Doctor Test\",\"email\":\""
                        + email
                        + "\",\"password\":\"Abc@123456\",\"licenseNumber\":\"TEST\",\"hospital\":\"Test\",\"specialtyId\":2}"))
        .andExpect(status().isOk());
    long user = db.queryForObject("SELECT id FROM public.users WHERE email=?", Long.class, email);
    long id = db.queryForObject("SELECT id FROM public.doctors WHERE user_id=?", Long.class, user);
    if (approved) db.update("UPDATE public.doctors SET approval_status='APPROVED' WHERE id=?", id);
    var login =
        mvc.perform(
                post("/api/auth/doctor/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Abc@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return new Actor(id, user, JsonPath.read(login.getResponse().getContentAsString(), "$.token"));
  }

  ResultActions request(Actor a, String json) throws Exception {
    return mvc.perform(
        post("/api/doctor/access-grants/request")
            .header("Authorization", "Bearer " + a.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  long send(Actor a, String code) throws Exception {
    var r = request(a, "{\"patientCode\":\"" + code + "\"}").andExpect(status().isOk()).andReturn();
    return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
  }

  @Test
  void requestUsesVerifiedDoctorAndPublicPatientWithoutExposingMedicalOrDemographicData()
      throws Exception {
    long p = patient("PC-ONE");
    var a = doctor("one@test.local", true);
    assertNotEquals(a.user(), a.doctor());
    var r =
        request(a, "{\"patientCode\":\"PC-ONE\",\"doctorId\":9999,\"status\":\"ACTIVE\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
    assertFalse(r.getResponse().getContentAsString().contains("Private Patient Name"));
    assertEquals(
        a.doctor(), db.queryForObject("SELECT doctor_id FROM public.access_grants", Long.class));
    assertEquals(p, db.queryForObject("SELECT patient_id FROM public.access_grants", Long.class));
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.access_grants", Integer.class));
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.patient_links", Integer.class));
  }

  @Test
  void repeatedPendingAndActiveRequestsReuseSameGrant() throws Exception {
    patient("PC-TWO");
    var a = doctor("two@test.local", true);
    long id = send(a, "PC-TWO");
    request(a, "{\"patientCode\":\"PC-TWO\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.existing").value(true))
        .andExpect(jsonPath("$.id").value(id));
    // Fixture simulates a Patient module committing approval; this is NOT a tested Patient API.
    db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", id);
    request(a, "{\"patientCode\":\"PC-TWO\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM public.access_grants", Integer.class));
  }

  @Test
  void patientApprovalRevokeExpiryAndRejectionAreReadWithoutAnyPrivateGrantCopy() throws Exception {
    patient("PC-THREE");
    var a = doctor("three@test.local", true);
    long id = send(a, "PC-THREE");
    visible(a, 0);
    db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", id);
    visible(a, 1);
    mvc.perform(
            get("/api/doctor/patients?q=PC-THREE").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].patientCode").value("PC-THREE"));
    db.update("UPDATE public.access_grants SET revoked=true WHERE id=?", id);
    visible(a, 0);
    db.update("UPDATE public.access_grants SET revoked=false,status='REVOKED' WHERE id=?", id);
    visible(a, 0);
    db.update("UPDATE public.access_grants SET status='REJECTED' WHERE id=?", id);
    visible(a, 0);
    db.update("UPDATE public.access_grants SET status='UNKNOWN' WHERE id=?", id);
    visible(a, 0);
    db.update(
        "UPDATE public.access_grants SET status='ACTIVE',expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(30)),
        id);
    visible(a, 0);
    db.update(
        "UPDATE public.access_grants SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),
        id);
    visible(a, 1);
  }

  void visible(Actor a, int total) throws Exception {
    mvc.perform(get("/api/doctor/patients").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(total));
  }

  @Test
  void twoConcurrentRequestsCreateExactlyOnePendingGrant() throws Exception {
    patient("PC-RACE");
    var a = doctor("race@test.local", true);
    var pool = Executors.newFixedThreadPool(2);
    var go = new CountDownLatch(1);
    try {
      Callable<Long> job =
          () -> {
            go.await();
            return service.request(a.principal(), "PC-RACE").id();
          };
      var one = pool.submit(job);
      var two = pool.submit(job);
      go.countDown();
      assertEquals(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
      assertEquals(
          1, db.queryForObject("SELECT count(*) FROM public.access_grants", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void differentDoctorsHaveIndependentGrantsAndRequestLists() throws Exception {
    patient("PC-FOUR");
    var a = doctor("four-a@test.local", true);
    var b = doctor("four-b@test.local", true);
    long x = send(a, "PC-FOUR"), y = send(b, "PC-FOUR");
    assertNotEquals(x, y);
    db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", x);
    visible(a, 1);
    visible(b, 0);
    mvc.perform(get("/api/doctor/access-grants").header("Authorization", "Bearer " + b.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(y));
  }

  @Test
  void pendingDisabledAndUnauthenticatedCannotRequestOrListPatients() throws Exception {
    patient("PC-FIVE");
    var a = doctor("five@test.local", false);
    request(a, "{\"patientCode\":\"PC-FIVE\"}").andExpect(status().isForbidden());
    visibleForbidden(a);
    db.update("UPDATE public.doctors SET approval_status='APPROVED'");
    db.update("UPDATE public.users SET enabled=false WHERE id=?", a.user());
    request(a, "{\"patientCode\":\"PC-FIVE\"}").andExpect(status().isForbidden());
    mvc.perform(
            post("/api/doctor/access-grants/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientCode\":\"PC-FIVE\"}"))
        .andExpect(status().isUnauthorized());
  }

  void visibleForbidden(Actor a) throws Exception {
    mvc.perform(get("/api/doctor/patients").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void sharedIdsCannotAccidentallyAccessOrWriteLegacyClinicalTables() throws Exception {
    patient("PC-SIX");
    var a = doctor("six@test.local", true);
    long id = send(a, "PC-SIX");
    db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", id);
    for (String path :
        List.of("/api/doctor/patients/1/records", "/api/doctor/records", "/api/doctor/records/1"))
      mvc.perform(get(path).header("Authorization", "Bearer " + a.token()))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("SHARED_RECORDS_NOT_READY"));
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.medical_records", Integer.class));
  }

  @Test
  void patientCodeValidationMissingCodeAndSearchDoNotEnumeratePatients() throws Exception {
    patient("PC-SEVEN");
    var a = doctor("seven@test.local", true);
    request(a, "{\"patientCode\":\" \"}").andExpect(status().isBadRequest());
    request(a, "{\"patientCode\":\"missing\"}").andExpect(status().isNotFound());
    visible(a, 0);
    mvc.perform(get("/api/doctor/integration").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaReady").value(true))
        .andExpect(jsonPath("$.sharedRecordsReady").value(true));
  }

  @Test
  void doctorFkToUsersUsesUserIdAndNeverProfileId() throws Exception {
    patient("PC-USER-FK");
    var a = doctor("user-fk@test.local", true);
    assertNotEquals(a.user(), a.doctor());
    db.execute("ALTER TABLE public.access_grants DROP CONSTRAINT public_grants_doctor_fk");
    db.execute(
        "ALTER TABLE public.access_grants ADD CONSTRAINT public_grants_doctor_fk FOREIGN KEY"
            + " (doctor_id) REFERENCES public.users(id)");
    try {
      long id = send(a, "PC-USER-FK");
      assertEquals(
          a.user(),
          db.queryForObject(
              "SELECT doctor_id FROM public.access_grants WHERE id=?", Long.class, id));
      db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", id);
      visible(a, 1);
    } finally {
      db.update("DELETE FROM public.access_grants");
      db.execute("ALTER TABLE public.access_grants DROP CONSTRAINT public_grants_doctor_fk");
      db.execute(
          "ALTER TABLE public.access_grants ADD CONSTRAINT public_grants_doctor_fk FOREIGN KEY"
              + " (doctor_id) REFERENCES public.doctors(id)");
    }
  }

  @Test
  void existingUniquePairConstraintDoesNotResurrectARevokedGrant() throws Exception {
    patient("PC-CLOSED");
    var a = doctor("closed@test.local", true);
    long id = send(a, "PC-CLOSED");
    db.update("UPDATE public.access_grants SET status='REVOKED',revoked=true WHERE id=?", id);
    db.execute(
        "ALTER TABLE public.access_grants ADD CONSTRAINT fixture_grant_pair"
            + " UNIQUE(patient_id,doctor_id)");
    try {
      request(a, "{\"patientCode\":\"PC-CLOSED\"}")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ACCESS_REQUEST_SCHEMA_CONFLICT"));
      assertEquals(
          "REVOKED",
          db.queryForObject(
              "SELECT status FROM public.access_grants WHERE id=?", String.class, id));
      assertEquals(
          1, db.queryForObject("SELECT count(*) FROM public.access_grants", Integer.class));
    } finally {
      db.execute("ALTER TABLE public.access_grants DROP CONSTRAINT fixture_grant_pair");
    }
  }

  @Test
  void screenshotColumnsWithDefaultsDoNotRequireAdditionalInsertFields() throws Exception {
    patient("PC-DEFAULTS");
    var a = doctor("defaults@test.local", true);
    long id = send(a, "PC-DEFAULTS");
    assertNotNull(
        db.queryForObject(
            "SELECT created_at FROM public.access_grants WHERE id=?",
            java.sql.Timestamp.class,
            id));
    assertNull(
        db.queryForObject("SELECT tx_hash FROM public.access_grants WHERE id=?", String.class, id));
    assertNull(
        db.queryForObject(
            "SELECT responded_at FROM public.access_grants WHERE id=?",
            java.sql.Timestamp.class,
            id));
    assertEquals(
        "FOLLOWING",
        db.queryForObject(
            "SELECT follow_status FROM public.patients WHERE patient_code='PC-DEFAULTS'",
            String.class));
  }

  @Test
  void timestampWithoutZoneKeepsWallTimeAndUsesDatabaseExpiryFlag() throws Exception {
    patient("PC-TIME");
    var a = doctor("time@test.local", true);
    long id = send(a, "PC-TIME");
    db.update(
        "UPDATE public.access_grants SET status='ACTIVE',expires_at=TIMESTAMP '2000-01-01 00:00:00'"
            + " WHERE id=?",
        id);
    mvc.perform(get("/api/doctor/access-grants").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].expired").value(true))
        .andExpect(jsonPath("$.content[0].expiresAt").value("2000-01-01T00:00"));
    visible(a, 0);
    db.update(
        "UPDATE public.access_grants SET expires_at=TIMESTAMP '2099-01-01 00:00:00' WHERE id=?",
        id);
    mvc.perform(get("/api/doctor/access-grants").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].expired").value(false));
    visible(a, 1);
  }

  long publicRecord(Actor author, long patient, String code) {
    db.update(
        "INSERT INTO"
            + " public.medical_records(record_code,patient_id,doctor_id,specialty_id,visit_at,reason,status,current_version,created_at)"
            + " VALUES (?,?,?,2,TIMESTAMP '2026-01-01 10:30:00','Fixture"
            + " visit','COMPLETED',2,TIMESTAMP '2026-01-01 10:00:00')",
        code,
        patient,
        author.doctor());
    long record =
        db.queryForObject(
            "SELECT id FROM public.medical_records WHERE record_code=?", Long.class, code);
    for (int n : List.of(1, 2))
      db.update(
          "INSERT INTO"
              + " public.record_versions(record_id,version_no,diagnosis,prescription,note,chain_status,created_by,created_at,data_hash)"
              + " VALUES (?,?,?,'Text prescription','Fixture note','CONFIRMED',?,TIMESTAMP"
              + " '2026-01-01 10:30:00',?)",
          record,
          n,
          "Clinical version " + n,
          author.user(),
          "0x" + "a".repeat(64));
    return record;
  }

  void activate(long grant) {
    db.update("UPDATE public.access_grants SET status='ACTIVE' WHERE id=?", grant);
  }

  @Test
  void sharedHistoryReadsPublicOwnRecordsAndRequiresCurrentGrant() throws Exception {
    long p = patient("PC-HISTORY");
    var a = doctor("history@test.local", true);
    var b = doctor("other-history@test.local", true);
    long grant = send(a, "PC-HISTORY");
    long own = publicRecord(a, p, "MR-OWN");
    publicRecord(b, p, "MR-OTHER");
    mvc.perform(get("/api/doctor/shared-records").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    activate(grant);
    mvc.perform(
            get("/api/doctor/shared-records?patientCode=PC-HISTORY&size=1")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(own))
        .andExpect(jsonPath("$.content[0].recordCode").value("MR-OWN"));
    mvc.perform(
            get("/api/doctor/shared-patients/" + p + "/records")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    db.update("UPDATE public.access_grants SET revoked=true WHERE id=?", grant);
    mvc.perform(get("/api/doctor/shared-records").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void sharedDetailsReadVersionSpecificMedicinesVitalsAndFileMetadataWithoutTrustingHash()
      throws Exception {
    long p = patient("PC-DETAIL");
    var a = doctor("details@test.local", true);
    activate(send(a, "PC-DETAIL"));
    long record = publicRecord(a, p, "MR-DETAIL");
    long v2 =
        db.queryForObject(
            "SELECT id FROM public.record_versions WHERE record_id=? AND version_no=2",
            Long.class,
            record);
    db.update(
        "INSERT INTO"
            + " public.prescription_items(record_version_id,sort_order,medicine_name,quantity_text)"
            + " VALUES (?,2,'Second medicine','2'),(?,1,'First medicine','1')",
        v2,
        v2);
    db.update(
        "INSERT INTO public.vital_signs(record_version_id,heart_rate,temperature) VALUES"
            + " (?,70,36.5)",
        v2);
    db.update(
        "INSERT INTO public.record_attachments(record_version_id,file_name,file_type,file_url)"
            + " VALUES (?,'result.pdf','PDF','https://private.test/DO_NOT_EXPOSE')",
        v2);
    var result =
        mvc.perform(
                get("/api/doctor/shared-records/" + record)
                    .header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version.versionNumber").value(2))
            .andExpect(jsonPath("$.version.createdByUserId").value(a.user()))
            .andExpect(jsonPath("$.version.medicines[0].medicineName").value("First medicine"))
            .andExpect(jsonPath("$.version.vitals.heartRate").value(70))
            .andExpect(jsonPath("$.version.attachments[0].fileName").value("result.pdf"))
            .andExpect(jsonPath("$.version.downloadAvailable").value(false))
            .andExpect(jsonPath("$.version.hashVerification").value("UNVERIFIED_PUBLIC_CANONICAL"))
            .andReturn();
    assertFalse(result.getResponse().getContentAsString().contains("DO_NOT_EXPOSE"));
    mvc.perform(
            get("/api/doctor/shared-records/" + record + "/versions/1")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Clinical version 1"))
        .andExpect(jsonPath("$.medicines").isEmpty());
    mvc.perform(
            get("/api/doctor/shared-records/" + record + "/versions?size=1")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].versionNumber").value(2));
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.medical_records", Integer.class));
  }

  @Test
  void recordIdAndPatientIdCannotBypassAnotherPatientsConsent() throws Exception {
    long p = patient("PC-ALLOWED"), other = patient("PC-HIDDEN");
    var a = doctor("idor@test.local", true);
    activate(send(a, "PC-ALLOWED"));
    long hidden = publicRecord(a, other, "MR-HIDDEN");
    for (String path :
        List.of(
            "/shared-records/" + hidden,
            "/shared-records/" + hidden + "/versions",
            "/shared-records/" + hidden + "/versions/1",
            "/shared-patients/" + other + "/records"))
      mvc.perform(get("/api/doctor" + path).header("Authorization", "Bearer " + a.token()))
          .andExpect(status().isForbidden());
  }

  @Test
  void revokeExpiryDisabledAndPendingBlockSharedMedicalContent() throws Exception {
    long p = patient("PC-CONSENT");
    var a = doctor("consent@test.local", true);
    long grant = send(a, "PC-CONSENT");
    long r = publicRecord(a, p, "MR-CONSENT");
    mvc.perform(
            get("/api/doctor/shared-records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    activate(grant);
    db.update(
        "UPDATE public.access_grants SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?",
        grant);
    mvc.perform(
            get("/api/doctor/shared-records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    db.update("UPDATE public.access_grants SET expires_at=NULL,status='REVOKED' WHERE id=?", grant);
    mvc.perform(
            get("/api/doctor/shared-records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    activate(grant);
    db.update("UPDATE public.doctors SET approval_status='PENDING' WHERE id=?", a.doctor());
    mvc.perform(
            get("/api/doctor/shared-records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    db.update("UPDATE public.doctors SET approval_status='APPROVED' WHERE id=?", a.doctor());
    db.update("UPDATE public.users SET enabled=false WHERE id=?", a.user());
    mvc.perform(
            get("/api/doctor/shared-records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/doctor/shared-records/" + r)).andExpect(status().isUnauthorized());
  }

  @Test
  void clinicalCapabilitiesKeepValidationAndBlockLegacyIdFallback() throws Exception {
    long p = patient("PC-READ-ONLY");
    var a = doctor("read-only@test.local", true);
    activate(send(a, "PC-READ-ONLY"));
    long r = publicRecord(a, p, "MR-READ-ONLY");
    mvc.perform(get("/api/doctor/integration").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sharedHistoryReady").value(true))
        .andExpect(jsonPath("$.sharedRecordsReady").value(true));
    mvc.perform(get("/api/doctor/records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(
            post("/api/doctor/shared-records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM public.medical_records", Integer.class));
  }
}
