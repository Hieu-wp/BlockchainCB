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
            "classpath:db/local-shared-schema.sql,classpath:db/local-doctor-schema.sql,classpath:db/local-blockchain-schema.sql,classpath:shared-grants-contract.sql,classpath:shared-clinical-read-contract.sql");
    r.add("jwt.secret", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("doctor.integration.shared-grants-enabled", () -> true);
    r.add("doctor.blockchain.enabled", () -> false);
    r.add("doctor.blockchain.deployment-reviewed", () -> false);
    r.add("doctor.blockchain.rpc-url", () -> "");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired SharedGrantService service;

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
        .andExpect(jsonPath("$.sharedRecordsReady").value(false));
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
  void readCapabilitiesDoNotEnablePublicWritesOrLegacyIdFallback() throws Exception {
    long p = patient("PC-READ-ONLY");
    var a = doctor("read-only@test.local", true);
    activate(send(a, "PC-READ-ONLY"));
    long r = publicRecord(a, p, "MR-READ-ONLY");
    mvc.perform(get("/api/doctor/integration").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sharedHistoryReady").value(true))
        .andExpect(jsonPath("$.sharedRecordsReady").value(false));
    mvc.perform(get("/api/doctor/records/" + r).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(
            post("/api/doctor/shared-records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM public.medical_records", Integer.class));
  }
}
