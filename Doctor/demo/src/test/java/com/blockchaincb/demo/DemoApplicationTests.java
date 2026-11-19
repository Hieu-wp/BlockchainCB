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
  @Autowired com.blockchaincb.demo.security.AuthPayloadService authEncryption;

  com.blockchaincb.demo.security.AuthPayloadService.Challenge authChallenge(String purpose) throws Exception {
    var response=mvc.perform(get("/api/auth/doctor/encryption-challenge").param("purpose",purpose))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.publicKey.d").doesNotExist()).andReturn();
    return tools.jackson.databind.json.JsonMapper.builder().build().readValue(
        response.getResponse().getContentAsString(), com.blockchaincb.demo.security.AuthPayloadService.Challenge.class);
  }

  String authEnvelope(com.blockchaincb.demo.security.AuthPayloadService.Challenge c, java.util.Map<String,Object> body) throws Exception {
    return "{\"payload\":\""+com.blockchaincb.demo.security.AuthPayloadServiceTest.encrypt(c,body)+"\"}";
  }

  @Test void encryptedAuthRegistersAndLogsInUsingExistingDoctorRules() throws Exception {
    var registration=java.util.Map.<String,Object>of("fullName","Encrypted Doctor","email","encrypted@example.test",
        "password","Abc@123456","licenseNumber","ENC-TEST","hospital","Fixture Hospital","specialtyId",2);
    mvc.perform(post("/api/auth/doctor/register/encrypted").contentType(MediaType.APPLICATION_JSON)
        .content(authEnvelope(authChallenge("register"),registration)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("PENDING"))
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON)
        .content(authEnvelope(authChallenge("login"),java.util.Map.of("email","encrypted@example.test","password","Abc@123456"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.approvalStatus").value("PENDING")).andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test void encryptedAuthConsumesFailedLoginAndRejectsPlaintextOnNewEndpoint() throws Exception {
    register("encrypted-bad@example.test");
    var body=authEnvelope(authChallenge("login"),java.util.Map.of("email","encrypted-bad@example.test","password","Wrong-password!"));
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_ENVELOPE_INVALID"));
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"encrypted-bad@example.test\",\"password\":\"Wrong-password!\"}")).andExpect(status().isBadRequest());
  }

  @Test void encryptedAuthStillBlocksDisabledAccountsAndInvalidCredentials() throws Exception {
    register("encrypted-disabled@example.test");
    db.update("UPDATE public.users SET enabled=false WHERE email='encrypted-disabled@example.test'");
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON)
        .content(authEnvelope(authChallenge("login"),java.util.Map.of("email","encrypted-disabled@example.test","password","Abc@123456"))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/doctor/login/encrypted").contentType(MediaType.APPLICATION_JSON)
        .content(authEnvelope(authChallenge("login"),java.util.Map.of("email","not-an-email","password","x"))))
        .andExpect(status().isBadRequest());
  }

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.blockchaincb.demo.blockchain.AnchorGateway anchorGateway;

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
    r.add("doctor.integration.shared-grants-enabled", () -> false);
    r.add(
        "spring.datasource.url", () -> "jdbc:h2:mem:doctor_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    r.add("jwt.secret", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("doctor.storage.local-enabled", () -> true);
    r.add("doctor.storage.local-root", () -> "./target/test-private-storage");
    r.add("doctor.blockchain.enabled", () -> true);
    r.add("doctor.blockchain.deployment-reviewed", () -> true);
    r.add("doctor.blockchain.rpc-url", () -> "http://127.0.0.1/mock-only");
    r.add("doctor.blockchain.reconcile-delay-ms", () -> "86400000");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder encoder;
  @Autowired AuthService auth;
  @Autowired HashService hash;
  @Autowired com.blockchaincb.demo.service.MedicalRecordService recordService;

  @BeforeEach
  void clean() {
    org.mockito.Mockito.reset(anchorGateway);
    assertTrue(db.queryForObject("SELECT H2VERSION()", String.class) != null);
    for (String table :
        List.of(
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

  record WalletFixture(String address, String challengeId, String signature) {}

  @Test void walletChallengeUsesApprovedOriginAndRejectsForeignOriginWithoutPersisting() throws Exception {
    long id=register("origin-wallet@example.test");String token=login("origin-wallet@example.test");
    String address="0x"+"1".repeat(40);
    mvc.perform(post("/api/doctor/wallet/challenges").header("Authorization","Bearer "+token)
        .header("Origin","http://127.0.0.1:5173").contentType(MediaType.APPLICATION_JSON)
        .content("{\"address\":\""+address+"\",\"origin\":\"http://127.0.0.1:5173\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.origin").value("http://127.0.0.1:5173"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Domain: 127.0.0.1:5173\nURI: http://127.0.0.1:5173\n")));
    for(String origin:List.of("https://untrusted.example","http://localhost:4173"))
      mvc.perform(post("/api/doctor/wallet/challenges").header("Authorization","Bearer "+token)
          .contentType(MediaType.APPLICATION_JSON).content("{\"address\":\""+address+"\",\"origin\":\""+origin+"\"}"))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WALLET_ORIGIN_NOT_ALLOWED"));
    mvc.perform(post("/api/doctor/wallet/challenges").header("Authorization","Bearer "+token)
        .header("Origin","https://untrusted.example").contentType(MediaType.APPLICATION_JSON)
        .content("{\"address\":\""+address+"\",\"origin\":\"http://localhost:5173\"}"))
        .andExpect(status().isForbidden());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_challenges",Integer.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_bindings",Integer.class));
    assertEquals("PENDING",db.queryForObject("SELECT approval_status FROM public.doctors WHERE id=?",String.class,id));
  }

  WalletFixture linkFixture(Actor actor) throws Exception {
    var wallet = challengeFixture(actor);
    verifyWallet(actor, wallet)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.walletAddress").value(wallet.address()));
    return wallet;
  }

  WalletFixture challengeFixture(Actor actor) throws Exception {
    var keys = org.web3j.crypto.Keys.createEcKeyPair();
    String address = "0x" + org.web3j.crypto.Keys.getAddress(keys);
    var response =
        mvc.perform(
                post("/api/doctor/wallet/challenges")
                    .header("Authorization", "Bearer " + actor.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\":\"" + address + "\",\"origin\":\"http://localhost:5173\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String id = JsonPath.read(response.getResponse().getContentAsString(), "$.challengeId");
    String message = JsonPath.read(response.getResponse().getContentAsString(), "$.message");
    var sig =
        org.web3j.crypto.Sign.signPrefixedMessage(
            message.getBytes(java.nio.charset.StandardCharsets.UTF_8), keys);
    byte[] out = new byte[65];
    System.arraycopy(sig.getR(), 0, out, 0, 32);
    System.arraycopy(sig.getS(), 0, out, 32, 32);
    out[64] = sig.getV()[0];
    String signature = org.web3j.utils.Numeric.toHexString(out);
    return new WalletFixture(address, id, signature);
  }

  ResultActions verifyWallet(Actor actor, WalletFixture wallet) throws Exception {
    return mvc.perform(
        post("/api/doctor/wallet/verify")
            .header("Authorization", "Bearer " + actor.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"challengeId\":\""
                    + wallet.challengeId()
                    + "\",\"signature\":\""
                    + wallet.signature()
                    + "\"}"));
  }

  @Test
  void walletSignatureIsPersistedAndReplayRejectedOverHttp() throws Exception {
    var actor = approved("wallet-http@example.test");
    var wallet = linkFixture(actor);
    mvc.perform(get("/api/doctor/me").header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.walletAddress").value(wallet.address()));
    mvc.perform(
            post("/api/doctor/wallet/verify")
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"challengeId\":\""
                        + wallet.challengeId()
                        + "\",\"signature\":\""
                        + wallet.signature()
                        + "\"}"))
        .andExpect(status().isConflict());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_bindings", Integer.class));
  }

  @Test
  void walletProofUserAddressAndConsumedNonceRollbackTogether() throws Exception {
    var actor = approved("atomic-wallet@example.test");
    var challenge = challengeFixture(actor);
    // Isolated H2 constraint intentionally fails the final users flush, AFTER binding was saved.
    db.execute(
        "ALTER TABLE public.users ADD CONSTRAINT fixture_no_wallet CHECK(wallet_address IS NULL)");
    try {
      verifyWallet(actor, challenge).andExpect(status().isConflict());
      assertEquals(
          0,
          db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_bindings", Integer.class));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT count(*) FROM public.users WHERE wallet_address IS NOT NULL", Integer.class));
      assertNull(
          db.queryForObject(
              "SELECT consumed_at FROM doctor_backend.wallet_challenges WHERE id=?",
              java.sql.Timestamp.class,
              challenge.challengeId()));
    } finally {
      db.execute("ALTER TABLE public.users DROP CONSTRAINT fixture_no_wallet");
    }
  }

  @Test
  void pendingDoctorLinksWalletButCannotUseMedicalOrAnchorApis() throws Exception {
    long id = register("pending-chain@example.test");
    String token = login("pending-chain@example.test");
    var actor = new Actor(id, token);
    mvc.perform(get("/api/doctor/blockchain/config").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.walletLinkPolicy").value("PENDING_APPROVED_V2_ORIGIN"))
        .andExpect(jsonPath("$.walletOrigins").isArray());
    var wallet = linkFixture(actor);
    mvc.perform(get("/api/doctor/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.walletAddress").value(wallet.address()))
        .andExpect(jsonPath("$.approvalStatus").value("PENDING"));
    mvc.perform(get("/api/doctor/wallet").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true))
        .andExpect(jsonPath("$.chainId").value(11155111))
        .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
    for (String path : List.of("/patients", "/records", "/records/1", "/records/1/versions"))
      mvc.perform(get("/api/doctor" + path).header("Authorization", "Bearer " + token))
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
    mvc.perform(
            post("/api/doctor/records/1/versions/1/anchor-payload")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    org.mockito.Mockito.verifyNoInteractions(anchorGateway);
  }

  @Test
  void walletEndpointsRequireJwtAndRejectInvalidTokens() throws Exception {
    for (String header : List.of("", "Bearer invalid")) {
      mvc.perform(
              post("/api/doctor/wallet/challenges")
                  .header("Authorization", header)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"address\":\"0x" + "1".repeat(40) + "\"}"))
          .andExpect(status().isUnauthorized());
      mvc.perform(get("/api/doctor/wallet").header("Authorization", header))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void anonymousWalletRequestsIncludeAuthenticationCode() throws Exception {
    mvc.perform(get("/api/doctor/wallet"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mvc.perform(get("/api/doctor/wallet").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  @Test
  void blockedDisabledRejectedWrongRoleAndUnknownStateCannotChallengeOrVerify() throws Exception {
    long id = register("restricted-wallet@example.test");
    var actor = new Actor(id, login("restricted-wallet@example.test"));
    var challenge = challengeFixture(actor);
    for (var change :
        java.util.Map.of(
                "UPDATE public.doctors SET status='BLOCKED'", "DOCTOR_BLOCKED",
                "UPDATE public.doctors SET approval_status='BLOCKED'", "DOCTOR_BLOCKED",
                "UPDATE public.doctors SET approval_status='REJECTED'", "WALLET_LINK_NOT_ALLOWED",
                "UPDATE public.doctors SET approval_status='UNKNOWN'", "DOCTOR_APPROVAL_INVALID",
                "UPDATE public.users SET enabled=false", "ACCOUNT_DISABLED",
                "UPDATE public.users SET role='PATIENT'", "ACCOUNT_ROLE_INVALID")
            .entrySet()) {
      db.update("UPDATE public.doctors SET status='PENDING',approval_status='PENDING'");
      db.update("UPDATE public.users SET enabled=true,role='DOCTOR'");
      db.update(change.getKey());
      mvc.perform(
              post("/api/doctor/wallet/challenges")
                  .header("Authorization", "Bearer " + actor.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"address\":\"" + challenge.address() + "\",\"origin\":\"http://localhost:5173\"}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(change.getValue()));
      verifyWallet(actor, challenge)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(change.getValue()));
    }
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_bindings", Integer.class));
    assertNull(
        db.queryForObject(
            "SELECT consumed_at FROM doctor_backend.wallet_challenges WHERE id=?",
            java.sql.Timestamp.class,
            challenge.challengeId()));
    org.mockito.Mockito.verifyNoInteractions(anchorGateway);
  }

  @Test
  void pendingCannotUseAnotherUsersChallengeOrStealTheirWallet() throws Exception {
    long first = register("first-wallet@example.test");
    var owner = new Actor(first, login("first-wallet@example.test"));
    var challenge = challengeFixture(owner);
    long second = register("second-wallet@example.test");
    var other = new Actor(second, login("second-wallet@example.test"));
    verifyWallet(other, challenge)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("CHALLENGE_NOT_OWNED"));
    verifyWallet(owner, challenge).andExpect(status().isOk());
    mvc.perform(
            post("/api/doctor/wallet/challenges")
                .header("Authorization", "Bearer " + other.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"" + challenge.address() + "\",\"origin\":\"http://localhost:5173\"}"))
        .andExpect(status().isConflict());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM doctor_backend.wallet_bindings", Integer.class));
  }

  @Test
  void pendingInvalidExpiredAndReplayedSignaturesAreRejected() throws Exception {
    long id = register("invalid-wallet@example.test");
    var actor = new Actor(id, login("invalid-wallet@example.test"));
    var challenge = challengeFixture(actor);
    verifyWallet(
            actor,
            new WalletFixture(challenge.address(), challenge.challengeId(), "0x" + "0".repeat(130)))
        .andExpect(status().isBadRequest());
    db.update(
        "UPDATE doctor_backend.wallet_challenges SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        challenge.challengeId());
    verifyWallet(actor, challenge).andExpect(status().isConflict());
    var linked = linkFixture(actor);
    verifyWallet(actor, linked).andExpect(status().isConflict());
    org.mockito.Mockito.verifyNoInteractions(anchorGateway);
  }

  @Test
  void savedAddressWithoutProofIsNotReportedAsVerified() throws Exception {
    long id = register("legacy-wallet@example.test");
    String token = login("legacy-wallet@example.test");
    db.update("UPDATE public.users SET wallet_address=?", "0x" + "1".repeat(40));
    mvc.perform(get("/api/doctor/wallet").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false))
        .andExpect(jsonPath("$.verifiedAt").isEmpty());
  }

  @Test
  void anchorHttpRoundTripPersistsReceiptAndOverlaysImmutableSnapshotStatus() throws Exception {
    var actor = approved("anchor-http@example.test");
    var wallet = linkFixture(actor);
    long patient = patient("patient-anchor@example.test");
    grant(actor.id(), patient, true, true);
    long id = create(actor, patient);
    long patientUser =
        db.queryForObject(
            "SELECT user_id FROM doctor_backend.patient_links WHERE id=?", Long.class, patient);
    String patientWallet = "0x" + "2".repeat(40);
    // Patient module boundary is populated only as an isolated H2 test fixture.
    db.update("UPDATE public.users SET wallet_address=? WHERE id=?", patientWallet, patientUser);
    db.update(
        "INSERT INTO"
            + " doctor_backend.wallet_bindings(user_id,address,chain_id,verified_at,proof_method)"
            + " VALUES (?,?,11155111,CURRENT_TIMESTAMP,'PERSONAL_SIGN')",
        patientUser,
        patientWallet);
    org.mockito.Mockito.when(anchorGateway.doctorRole(wallet.address())).thenReturn(true);
    org.mockito.Mockito.when(anchorGateway.writePermission(patientWallet, wallet.address()))
        .thenReturn(true);
    org.mockito.Mockito.when(anchorGateway.state(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(
            new com.blockchaincb.demo.blockchain.AnchorGateway.State(
                "0x" + "0".repeat(40), 0, com.blockchaincb.demo.blockchain.ChainSettings.ZERO));
    org.mockito.Mockito.when(anchorGateway.head()).thenReturn(100L);
    org.mockito.Mockito.when(
            anchorGateway.scan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new com.blockchaincb.demo.blockchain.AnchorGateway.Scan(null, 100L));
    String base = "/api/doctor/records/" + id + "/versions/1";
    var payload =
        mvc.perform(
                post(base + "/anchor-payload").header("Authorization", "Bearer " + actor.token()))
            .andExpect(status().isOk())
            .andReturn();
    String key = JsonPath.read(payload.getResponse().getContentAsString(), "$.recordKey");
    assertTrue(key.matches("0x[0-9a-f]{64}"));
    mvc.perform(post(base + "/anchor-payload").header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isConflict());
    assertEquals(
        key,
        db.queryForObject(
            "SELECT record_key FROM doctor_backend.record_anchors WHERE record_id=?",
            String.class,
            id));
    String tx = "0x" + "5".repeat(64);
    org.mockito.Mockito.when(
            anchorGateway.verify(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(tx)))
        .thenReturn(
            new com.blockchaincb.demo.blockchain.AnchorGateway.Verified(
                "CONFIRMED", "VERIFIED", 100L, "0x" + "6".repeat(64)));
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(base + "/anchor-transactions")
                  .header("Authorization", "Bearer " + actor.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"transactionHash\":\"" + tx + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM doctor_backend.anchor_attempts WHERE record_id=?",
            Integer.class,
            id));
    mvc.perform(get("/api/doctor/records/" + id).header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version.blockchainStatus").value("CONFIRMED"));
    assertEquals(
        "NOT_SUBMITTED",
        db.queryForObject(
            "SELECT blockchain_status FROM doctor_backend.record_versions WHERE record_id=?",
            String.class,
            id));
    String canonical =
        db.queryForObject(
            "SELECT canonical_content FROM doctor_backend.record_versions WHERE record_id=?",
            String.class,
            id);
    String rewritten = canonical.replace("7:Initial", "7:Changed");
    assertNotEquals(canonical, rewritten);
    db.update(
        "UPDATE doctor_backend.record_versions SET"
            + " diagnosis='Changed',canonical_content=?,content_hash=? WHERE record_id=?",
        rewritten,
        hash.sha256(rewritten),
        id);
    mvc.perform(get(base + "/hash").header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storedSnapshotMatchesHash").value(true))
        .andExpect(jsonPath("$.currentContentMatchesHash").value(false));
    mvc.perform(get(base + "/anchor-status").header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isConflict());
    assertTrue(
        db.queryForList("SELECT resource FROM doctor_backend.audit_logs", String.class).stream()
            .noneMatch(
                resource -> resource.contains(wallet.signature()) || resource.contains("Initial")));
    db.update(
        "UPDATE doctor_backend.access_grants SET revoked_at=CURRENT_TIMESTAMP WHERE patient_id=?",
        patient);
    mvc.perform(get(base + "/anchor-status").header("Authorization", "Bearer " + actor.token()))
        .andExpect(status().isForbidden());
  }
}
