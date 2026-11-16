package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.blockchaincb.demo.dto.RegisterRequest;
import com.blockchaincb.demo.service.AuthService;
import com.blockchaincb.demo.service.HashService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(DemoApplicationTests.IsolatedDatabase.class)
class DemoApplicationTests {
  @org.springframework.boot.test.context.TestConfiguration
  static class IsolatedDatabase {
    @org.springframework.context.annotation.Bean
    javax.sql.DataSource dataSource() {
      var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
      ds.setDriverClassName("org.h2.Driver");
      ds.setUrl("jdbc:h2:mem:doctor_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
      ds.setUsername("sa");
      ds.setPassword("");
      return ds;
    }
  }

  // Highest precedence: tests cannot use DB_PASSWORD or an inherited external JDBC URL.
  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url", () -> "jdbc:h2:mem:doctor_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    r.add("jwt.secret", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("doctor.storage.local-enabled", () -> true);
    r.add("doctor.storage.local-root", () -> "./target/test-private-storage");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder encoder;
  @Autowired AuthService auth;
  @Autowired HashService hash;
  @Autowired com.blockchaincb.demo.service.MedicalRecordService recordService;

  @BeforeEach
  void clean() {
    assertTrue(db.queryForObject("SELECT H2VERSION()", String.class) != null);
    for (String table :
        List.of(
            "doctor_backend.audit_logs",
            "doctor_backend.version_attachments",
            "doctor_backend.record_versions",
            "doctor_backend.attachments",
            "doctor_backend.medical_records",
            "doctor_backend.access_grants",
            "doctor_backend.patient_links",
            "public.doctors",
            "public.users")) db.update("DELETE FROM " + table);
  }

  String registration(String email) {
    return """
{"fullName":"Doctor Test","email":"%s","password":"Abc@123456","licenseNumber":"TEST-LICENSE","hospital":"Test Hospital","specialtyId":2}
"""
        .formatted(email);
  }

  long register(String email) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/doctor/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
            .andReturn();
    return number(result, "$.id");
  }

  long number(MvcResult result, String path) throws Exception {
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
  }

  String login(String email) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/doctor/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Abc@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  record Actor(long id, String token) {}

  Actor approved(String email) throws Exception {
    long id = register(email);
    db.update("UPDATE public.doctors SET approval_status='APPROVED' WHERE id=?", id);
    return new Actor(id, login(email));
  }

  long patient(String email) {
    db.update(
        "INSERT INTO public.users(email,password_hash,full_name,role,enabled) VALUES"
            + " (?,?,?,'PATIENT',true)",
        email,
        encoder.encode("Abc@123456"),
        "Patient Test");
    long user = db.queryForObject("SELECT id FROM public.users WHERE email=?", Long.class, email);
    db.update("INSERT INTO doctor_backend.patient_links(user_id) VALUES (?)", user);
    return db.queryForObject(
        "SELECT id FROM doctor_backend.patient_links WHERE user_id=?", Long.class, user);
  }

  long grant(long doctor, long patient, boolean create, boolean update) {
    db.update(
        "INSERT INTO"
            + " doctor_backend.access_grants(patient_id,doctor_id,can_read,can_create,can_update,valid_from)"
            + " VALUES (?,?,true,?,?,?)",
        patient,
        doctor,
        create,
        update,
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
    return db.queryForObject("SELECT MAX(id) FROM doctor_backend.access_grants", Long.class);
  }

  String content(long patient, String diagnosis) {
    return """
{"patientId":%d,"examDate":"2026-01-01","diagnosis":"%s","prescription":"Rx","testResults":"","notes":"","attachmentIds":[]}
"""
        .formatted(patient, diagnosis);
  }

  long create(Actor actor, long patient) throws Exception {
    return number(
        mvc.perform(
                post("/api/doctor/records")
                    .header("Authorization", "Bearer " + actor.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content(patient, "Initial")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value(actor.id()))
            .andExpect(jsonPath("$.version.blockchainStatus").value("NOT_SUBMITTED"))
            .andReturn(),
        "$.id");
  }

  @Test
  void registrationPersistsSameBcryptHashAndDuplicateIsConflict() throws Exception {
    register("register@example.test");
    String userHash =
        db.queryForObject(
            "SELECT password_hash FROM public.users WHERE email='register@example.test'",
            String.class);
    assertTrue(encoder.matches("Abc@123456", userHash));
    assertEquals(
        userHash,
        db.queryForObject(
            "SELECT password FROM public.doctors WHERE email='register@example.test'",
            String.class));
    mvc.perform(
            post("/api/auth/doctor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration("register@example.test")))
        .andExpect(status().isConflict());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM public.users", Integer.class));
  }

  @Test
  void transactionRollsBackUserIfDoctorInsertFails() {
    var r = new RegisterRequest();
    r.setEmail("rollback@example.test");
    r.setPassword("Abc@123456");
    r.setFullName("Name");
    r.setSpecialtyId(2L);
    r.setLicenseNumber("LICENSE");
    r.setHospital(null);
    assertThrows(RuntimeException.class, () -> auth.register(r));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM public.users", Integer.class));
  }

  @Test
  void rejectsInvalidSpecialtyAndValidationWithoutWrites() throws Exception {
    mvc.perform(
            post("/api/auth/doctor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registration("bad@example.test")
                        .replace("\"specialtyId\":2", "\"specialtyId\":999")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/doctor/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM public.users", Integer.class));
  }

  @Test
  void loginUsesUsersHashEvenIfLegacyHashDiffers() throws Exception {
    register("login@example.test");
    db.update("UPDATE public.doctors SET password=?", encoder.encode("OtherPassword"));
    login("login@example.test");
    mvc.perform(
            post("/api/auth/doctor/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.test\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void pendingCanViewAndEditOwnProfileButCannotUseRecords() throws Exception {
    register("pending@example.test");
    String token = login("pending@example.test");
    mvc.perform(get("/api/doctor/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(
            patch("/api/doctor/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"New Name\",\"hospital\":\"New Hospital\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("New Name"));
    mvc.perform(get("/api/doctor/patients").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content(1, "Denied")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/doctor/records/1/versions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"content\":" + content(1, "Denied") + "}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingAndMalformedTokensAre401() throws Exception {
    mvc.perform(get("/api/doctor/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/doctor/me").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleDisabledAndApprovalAreReloadedOnEveryRequest() throws Exception {
    var a = approved("live@example.test");
    String header = "Bearer " + a.token();
    db.update("UPDATE public.users SET enabled=false");
    mvc.perform(get("/api/doctor/me").header("Authorization", header))
        .andExpect(status().isForbidden());
    db.update("UPDATE public.users SET enabled=true,role='PATIENT'");
    mvc.perform(get("/api/doctor/me").header("Authorization", header))
        .andExpect(status().isForbidden());
    db.update("UPDATE public.users SET role='DOCTOR'");
    db.update("UPDATE public.doctors SET approval_status='PENDING'");
    mvc.perform(get("/api/doctor/patients").header("Authorization", header))
        .andExpect(status().isForbidden());
    db.update("UPDATE public.doctors SET approval_status='BLOCKED'");
    mvc.perform(get("/api/doctor/me").header("Authorization", header))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectedCanSeeStatusButNotPatients() throws Exception {
    long id = register("rejected@example.test");
    db.update("UPDATE public.doctors SET approval_status='REJECTED' WHERE id=?", id);
    String header = "Bearer " + login("rejected@example.test");
    mvc.perform(get("/api/doctor/me").header("Authorization", header))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));
    mvc.perform(get("/api/doctor/patients").header("Authorization", header))
        .andExpect(status().isForbidden());
  }

  @Test
  void patientRoleCannotLoginThroughDoctorEndpoint() throws Exception {
    patient("patient@example.test");
    mvc.perform(
            post("/api/auth/doctor/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"patient@example.test\",\"password\":\"Abc@123456\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void patientListIsScopedFilteredAndPaged() throws Exception {
    var a = approved("list@example.test");
    long allowed = patient("allowed@example.test");
    patient("hidden@example.test");
    grant(a.id(), allowed, true, true);
    mvc.perform(
            get("/api/doctor/patients?q=Patient&page=0&size=1")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(allowed));
    mvc.perform(get("/api/doctor/patients?size=101").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void versionsAreAppendOnlyLinkedHashedAndStaleUpdateConflicts() throws Exception {
    var a = approved("version@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    long id = create(a, p);
    String old =
        db.queryForObject(
            "SELECT content_hash FROM doctor_backend.record_versions WHERE record_id=? AND"
                + " version_number=1",
            String.class,
            id);
    String update = "{\"expectedVersion\":1,\"content\":" + content(p, "Updated") + "}";
    mvc.perform(
            post("/api/doctor/records/" + id + "/versions")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentVersion").value(2))
        .andExpect(jsonPath("$.version.previousHash").value(old));
    assertEquals(
        "Initial",
        db.queryForObject(
            "SELECT diagnosis FROM doctor_backend.record_versions WHERE record_id=? AND"
                + " version_number=1",
            String.class,
            id));
    String canonical =
        db.queryForObject(
            "SELECT canonical_content FROM doctor_backend.record_versions WHERE record_id=? AND"
                + " version_number=2",
            String.class,
            id);
    assertEquals(
        hash.sha256(canonical),
        db.queryForObject(
            "SELECT content_hash FROM doctor_backend.record_versions WHERE record_id=? AND"
                + " version_number=2",
            String.class,
            id));
    mvc.perform(
            post("/api/doctor/records/" + id + "/versions")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isConflict());
    mvc.perform(
            get("/api/doctor/records/" + id + "/versions")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(
            get("/api/doctor/records/" + id + "/versions/2/hash")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storedSnapshotMatchesHash").value(true));
    mvc.perform(get("/api/doctor/records").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void revocationStopsFormerCreatorFromAllProtectedReadsAndWrites() throws Exception {
    var a = approved("revoke@example.test");
    long p = patient("p@example.test");
    long g = grant(a.id(), p, true, true);
    long id = create(a, p);
    db.update("UPDATE doctor_backend.access_grants SET revoked_at=CURRENT_TIMESTAMP WHERE id=?", g);
    for (String path :
        List.of(
            "/api/doctor/records/" + id,
            "/api/doctor/records/" + id + "/versions",
            "/api/doctor/records/" + id + "/versions/1/hash",
            "/api/doctor/patients/" + p + "/records"))
      mvc.perform(get(path).header("Authorization", "Bearer " + a.token()))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/doctor/records").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            post("/api/doctor/records/" + id + "/versions")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"content\":" + content(p, "Denied") + "}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anotherDoctorCannotUseGuessedPatientOrRecordIds() throws Exception {
    var a = approved("owner@example.test");
    var b = approved("other@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    long id = create(a, p);
    mvc.perform(get("/api/doctor/records/" + id).header("Authorization", "Bearer " + b.token()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + b.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content(p, "Denied")))
        .andExpect(status().isForbidden());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM doctor_backend.medical_records", Integer.class));
  }

  @Test
  void readPermissionDoesNotImplyWritePermission() throws Exception {
    var a = approved("read@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, false, false);
    mvc.perform(
            get("/api/doctor/patients/" + p + "/records")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content(p, "Denied")))
        .andExpect(status().isForbidden());
  }

  @Test
  void expiredAndFutureGrantsDoNotAuthorize() throws Exception {
    var a = approved("expired@example.test");
    long p = patient("p@example.test");
    long g = grant(a.id(), p, true, true);
    db.update(
        "UPDATE doctor_backend.access_grants SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(10)),
        g);
    mvc.perform(
            get("/api/doctor/patients/" + p + "/records")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
    db.update(
        "UPDATE doctor_backend.access_grants SET expires_at=null,valid_from=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(600)),
        g);
    mvc.perform(get("/api/doctor/patients").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void attachmentUploadDownloadAndRevocationAreProtected() throws Exception {
    var a = approved("files@example.test");
    long p = patient("p@example.test");
    long g = grant(a.id(), p, true, true);
    byte[] pdf = "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var result =
        mvc.perform(
                multipart("/api/doctor/patients/" + p + "/attachments")
                    .file(new MockMultipartFile("file", "../../test.pdf", "application/pdf", pdf))
                    .header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isCreated())
            .andReturn();
    long file = number(result, "$.id");
    var created =
        mvc.perform(
                post("/api/doctor/records")
                    .header("Authorization", "Bearer " + a.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        content(p, "With file")
                            .replace("\"attachmentIds\":[]", "\"attachmentIds\":[" + file + "]")))
            .andExpect(status().isCreated())
            .andReturn();
    long id = number(created, "$.id");
    String path = "/api/doctor/records/" + id + "/versions/1/attachments/" + file;
    mvc.perform(get(path).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf));
    db.update("UPDATE doctor_backend.access_grants SET revoked_at=CURRENT_TIMESTAMP WHERE id=?", g);
    mvc.perform(get(path).header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void auditContainsMetadataAndRecordsDeniedResult() throws Exception {
    var a = approved("audit@example.test");
    long p = patient("p@example.test");
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content(p, "SECRET DIAGNOSIS")))
        .andExpect(status().isForbidden());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM doctor_backend.audit_logs WHERE outcome='DENIED'",
            Integer.class));
    assertTrue(
        db.queryForList("SELECT resource FROM doctor_backend.audit_logs", String.class).stream()
            .allMatch(s -> !s.contains("SECRET")));
  }

  @Test
  void malformedJsonAndMissingRecordReturnSafeErrors() throws Exception {
    var a = approved("errors@example.test");
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Du lieu yeu cau khong hop le"));
    mvc.perform(get("/api/doctor/records/99999").header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void hashVerificationDetectsChangedMedicalFieldsAndBlocksAppend() throws Exception {
    var a = approved("tamper@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    long id = create(a, p);
    db.update(
        "UPDATE doctor_backend.record_versions SET diagnosis='Changed outside application' WHERE"
            + " record_id=?",
        id);
    mvc.perform(
            get("/api/doctor/records/" + id + "/versions/1/hash")
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storedSnapshotMatchesHash").value(true))
        .andExpect(jsonPath("$.currentContentMatchesHash").value(false));
    mvc.perform(
            post("/api/doctor/records/" + id + "/versions")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"content\":" + content(p, "Update") + "}"))
        .andExpect(status().isConflict());
  }

  @Test
  void concurrentWritersProduceOneNewVersion() throws Exception {
    var a = approved("concurrent@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    long id = create(a, p);
    long user =
        db.queryForObject("SELECT user_id FROM public.doctors WHERE id=?", Long.class, a.id());
    var principal = new com.blockchaincb.demo.security.DoctorPrincipal(user, a.id());
    var req =
        new com.blockchaincb.demo.dto.UpdateRecordRequest(
            1,
            new com.blockchaincb.demo.dto.MedicalRecordRequest(
                p, java.time.LocalDate.of(2026, 1, 1), "Concurrent", "Rx", "", "", List.of()));
    var start = new java.util.concurrent.CountDownLatch(1);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<Boolean> writer =
        () -> {
          ready.countDown();
          start.await();
          try {
            recordService.update(principal, id, req);
            return true;
          } catch (com.blockchaincb.demo.exception.ApiException e) {
            assertEquals(409, e.getStatus());
            return false;
          } catch (org.springframework.dao.OptimisticLockingFailureException
              | org.springframework.dao.DataIntegrityViolationException e) {
            return false;
          }
        };
    try {
      var one = executor.submit(writer);
      var two = executor.submit(writer);
      assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      int successes =
          (one.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
              + (two.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
      assertEquals(1, successes);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
    assertEquals(
        2,
        db.queryForObject(
            "SELECT count(*) FROM doctor_backend.record_versions WHERE record_id=?",
            Integer.class,
            id));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT current_version FROM doctor_backend.medical_records WHERE id=?",
            Integer.class,
            id));
  }

  @Test
  void apiCannotChangeRoleApprovalOrDoctorIdentity() throws Exception {
    var a = approved("identity@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    content(p, "Safe")
                        .replace("\"patientId\":", "\"doctorId\":99999,\"patientId\":")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy").value(a.id()));
    mvc.perform(
            patch("/api/doctor/me")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"Name\",\"hospital\":\"Hospital\",\"role\":\"ADMIN\",\"approvalStatus\":\"BLOCKED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    assertEquals(
        "DOCTOR",
        db.queryForObject(
            "SELECT role FROM public.users WHERE email='identity@example.test'", String.class));
  }

  @Test
  void attachmentFromDifferentPatientIsRejectedAndRecordRolledBack() throws Exception {
    var a = approved("cross-file@example.test");
    long p = patient("p@example.test");
    long other = patient("other@example.test");
    grant(a.id(), p, true, true);
    grant(a.id(), other, true, true);
    long file =
        number(
            mvc.perform(
                    multipart("/api/doctor/patients/" + p + "/attachments")
                        .file(
                            new MockMultipartFile(
                                "file",
                                "sample.pdf",
                                "application/pdf",
                                "%PDF-1.4 fixture"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isCreated())
                .andReturn(),
            "$.id");
    mvc.perform(
            post("/api/doctor/records")
                .header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    content(other, "Wrong attachment")
                        .replace("\"attachmentIds\":[]", "\"attachmentIds\":[" + file + "]")))
        .andExpect(status().isForbidden());
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.medical_records", Integer.class));
  }

  @Test
  void unsupportedAttachmentIsRejectedWithoutStorageMetadata() throws Exception {
    var a = approved("bad-file@example.test");
    long p = patient("p@example.test");
    grant(a.id(), p, true, true);
    mvc.perform(
            multipart("/api/doctor/patients/" + p + "/attachments")
                .file(
                    new MockMultipartFile(
                        "file",
                        "fake.pdf",
                        "application/pdf",
                        "<script>bad</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isBadRequest());
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.attachments", Integer.class));
  }
}
