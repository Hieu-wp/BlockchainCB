package com.blockchaincb;

import com.blockchaincb.service.AdminDoctorService;
import com.blockchaincb.service.AdminUserService;
import com.blockchaincb.service.AdminAuditLogService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ chạy bằng profile postgres-it: PostgreSQL mới, port ngẫu nhiên, không nhận URL bên ngoài. */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application-test.properties",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:postgres-test-schema.sql"
})
class PostgresAdminIT {
    static final EmbeddedPostgres postgres = khoiTaoPostgres();
    static EmbeddedPostgres khoiTaoPostgres() {
        try {
            var thuMuc = java.nio.file.Path.of("target").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(thuMuc);
            return EmbeddedPostgres.builder().setPort(0)
                    .setServerConfig("listen_addresses", "127.0.0.1")
                    .setOverrideWorkingDirectory(thuMuc.resolve("postgres-binaries").toFile())
                    .setDataDirectory(java.nio.file.Files.createTempDirectory(thuMuc, "pg-test-data-"))
                    .setPGStartupWait(java.time.Duration.ofSeconds(20)).start();
        }
        catch (IOException loi) { throw new IllegalStateException("Không khởi tạo được PostgreSQL test riêng", loi); }
    }
    @DynamicPropertySource static void cauHinhDatabaseRieng(DynamicPropertyRegistry thuocTinh) {
        thuocTinh.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        thuocTinh.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        thuocTinh.add("spring.datasource.username", () -> "postgres");
        thuocTinh.add("spring.datasource.password", () -> "postgres");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminUserService nguoiDung;
    @Autowired AdminDoctorService bacSi;
    @Autowired AdminAuditLogService nhatKy;
    @Autowired PlatformTransactionManager quanLyGiaoDich;
    @Autowired DataSource dataSource;
    @PersistenceContext EntityManager entityManager;

    static void dangNhapAdmin() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.test", "test-only", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @BeforeEach void duLieuRiengChoMoiTest() {
        jdbc.execute("ALTER TABLE public.admin_audit_logs DROP CONSTRAINT IF EXISTS test_audit_fail");
        jdbc.execute("ALTER TABLE public.doctors DROP CONSTRAINT IF EXISTS test_update_fail");
        jdbc.execute("TRUNCATE public.admin_audit_logs, public.doctors, public.users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO public.users(id,email,password_hash,full_name,role,enabled) VALUES "
                + "(1,'admin@example.test','test-password','Admin test','ADMIN',true),"
                + "(2,'doctor@example.test','test-password','Doctor test','DOCTOR',true),"
                + "(3,'patient@example.test','test-password','Patient test','PATIENT',true),"
                + "(4,'admin2@example.test','test-password','Admin test 2','ADMIN',true)");
        jdbc.update("INSERT INTO public.doctors(id,user_id,specialty_id,approval_status,email,full_name,hospital,license_number,password,status) "
                + "VALUES (7,2,1,'PENDING','doctor@example.test','Doctor test','Hospital test','GP-TEST','legacy-test-secret','PENDING')");
        dangNhapAdmin();
    }
    @AfterEach void xoaPhienTest() { SecurityContextHolder.clearContext(); }
    @AfterAll static void dungPostgresRieng() throws IOException { postgres.close(); }

    String trangThaiBacSi() { return jdbc.queryForObject("SELECT approval_status FROM public.doctors WHERE id=7", String.class); }
    boolean patientHoatDong() { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM public.users WHERE id=3", Boolean.class)); }
    long soAudit() { return jdbc.queryForObject("SELECT count(*) FROM public.admin_audit_logs", Long.class); }
    void maLoi(int ma, Runnable thaoTac) { assertEquals(ma, assertThrows(ResponseStatusException.class, thaoTac::run).getStatusCode().value()); }

    @Test void jpaVaJdbcChungPostgresTransactionThuc() {
        JpaTransactionManager boQuanLy = assertInstanceOf(JpaTransactionManager.class, quanLyGiaoDich);
        assertSame(dataSource, boQuanLy.getDataSource());
        new TransactionTemplate(quanLyGiaoDich).executeWithoutResult(trangThai -> {
            Number jpa = (Number) entityManager.createNativeQuery("SELECT txid_current()").getSingleResult();
            Long sql = jdbc.queryForObject("SELECT txid_current()", Long.class);
            assertEquals(jpa.longValue(), sql.longValue());
        });
    }
    @Test void rejectVaAuditCungCommitKhongMatHoSoGoc() {
        var phanHoi = bacSi.tuChoiBacSi(7L, "  Giấy phép chưa hợp lệ  ");
        assertEquals("REJECTED", phanHoi.trangThaiPheDuyet());
        assertEquals("REJECTED", trangThaiBacSi()); assertEquals(1, soAudit());
        var dong = jdbc.queryForMap("SELECT * FROM public.admin_audit_logs");
        assertEquals("DOCTOR_REJECT", dong.get("action")); assertEquals("DOCTOR", dong.get("target_type"));
        assertEquals(1L, ((Number) dong.get("admin_id")).longValue());
        assertNotNull(dong.get("created_at")); assertTrue(dong.get("description").toString().endsWith("Giấy phép chưa hợp lệ"));
        var hoSo = jdbc.queryForMap("SELECT approved_by,approved_at,password,status,license_number FROM public.doctors WHERE id=7");
        assertNull(hoSo.get("approved_by")); assertNull(hoSo.get("approved_at"));
        assertEquals("legacy-test-secret", hoSo.get("password")); assertEquals("PENDING", hoSo.get("status"));
        assertEquals("GP-TEST", phanHoi.soGiayPhep());
    }
    @Test void rejectAuditLoiRollbackJpaUpdate() {
        jdbc.execute("ALTER TABLE public.admin_audit_logs ADD CONSTRAINT test_audit_fail CHECK (false) NOT VALID");
        assertThrows(DataAccessException.class, () -> bacSi.tuChoiBacSi(7L, "Lý do thử nghiệm"));
        assertEquals("PENDING", trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void rejectUpdateLoiKhongCoAuditThanhCong() {
        jdbc.execute("ALTER TABLE public.doctors ADD CONSTRAINT test_update_fail CHECK (approval_status <> 'REJECTED') NOT VALID");
        assertThrows(DataAccessException.class, () -> bacSi.tuChoiBacSi(7L, "Lý do thử nghiệm"));
        assertEquals("PENDING", trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void patientLockUnlockVaAuditCungCommit() {
        assertEquals("INACTIVE", nguoiDung.khoaNguoiDung(3L, "Lý do thử nghiệm").trangThai());
        assertFalse(patientHoatDong());
        assertEquals("ACTIVE", nguoiDung.moKhoaNguoiDung(3L).trangThai());
        assertTrue(patientHoatDong()); assertEquals(2, soAudit());
    }
    @Test void patientLockAuditLoiPhaiRollback() {
        jdbc.execute("ALTER TABLE public.admin_audit_logs ADD CONSTRAINT test_audit_fail CHECK (false) NOT VALID");
        assertThrows(DataAccessException.class, () -> nguoiDung.khoaNguoiDung(3L, "Lý do"));
        assertTrue(patientHoatDong()); assertEquals(0, soAudit());
    }
    @Test void patientUnlockAuditLoiPhaiRollback() {
        jdbc.update("UPDATE public.users SET enabled=false WHERE id=3");
        jdbc.execute("ALTER TABLE public.admin_audit_logs ADD CONSTRAINT test_audit_fail CHECK (false) NOT VALID");
        assertThrows(DataAccessException.class, () -> nguoiDung.moKhoaNguoiDung(3L));
        assertFalse(patientHoatDong()); assertEquals(0, soAudit());
    }
    @Test void adminDoctorKhongBiKhoaQuaPatientApi() {
        maLoi(403, () -> nguoiDung.khoaNguoiDung(1L, "Lý do"));
        maLoi(403, () -> nguoiDung.khoaNguoiDung(4L, "Lý do"));
        maLoi(409, () -> nguoiDung.khoaNguoiDung(2L, "Lý do"));
        assertEquals(0, soAudit());
    }
    @Test void khongTimThayVaLyDoSaiKhongGhiAudit() {
        maLoi(404, () -> nguoiDung.khoaNguoiDung(999L, "Lý do"));
        maLoi(400, () -> nguoiDung.khoaNguoiDung(3L, " "));
        maLoi(400, () -> nguoiDung.khoaNguoiDung(3L, "a".repeat(351)));
        maLoi(404, () -> bacSi.tuChoiBacSi(999L, "Lý do"));
        assertEquals(0, soAudit());
    }
    @Test void khongLapLaiLockUnlock() {
        maLoi(409, () -> nguoiDung.moKhoaNguoiDung(3L));
        nguoiDung.khoaNguoiDung(3L, "Lý do");
        maLoi(409, () -> nguoiDung.khoaNguoiDung(3L, "Lý do khác"));
        assertEquals(1, soAudit());
    }
    @ParameterizedTest @ValueSource(strings = {"APPROVED", "REJECTED"})
    void khongTuChoiLaiHoSoDaXuLy(String trangThai) {
        jdbc.update("UPDATE public.doctors SET approval_status=? WHERE id=7", trangThai);
        maLoi(409, () -> bacSi.tuChoiBacSi(7L, "Lý do"));
        assertEquals(trangThai, trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void thaoTacBlockchainKhongGhiThanhCongGia() {
        maLoi(501, () -> bacSi.duyetBacSi(7L));
        jdbc.update("UPDATE public.doctors SET approval_status='APPROVED' WHERE id=7");
        maLoi(501, () -> bacSi.khoaBacSi(7L, "Lý do"));
        maLoi(501, () -> bacSi.moKhoaBacSi(7L));
        assertEquals("APPROVED", trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void auditBatBuocCoTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () ->
                nhatKy.ghiNhatKy(AdminAuditLogService.HanhDongQuanTri.USER_LOCK,
                        AdminAuditLogService.LoaiDoiTuong.USER, 3L, "Test"));
    }
    @Test void adminKhongHoatDongKhongGhiDuoc() {
        jdbc.update("UPDATE public.users SET enabled=false WHERE id=1");
        maLoi(403, () -> bacSi.tuChoiBacSi(7L, "Lý do"));
        assertEquals("PENDING", trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void serviceKhongChoPhepTaiKhoanKhongPhaiAdmin() {
        for (String quyen : List.of("ROLE_PATIENT", "ROLE_DOCTOR")) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "doctor@example.test", "test-only", List.of(new SimpleGrantedAuthority(quyen))));
            maLoi(403, () -> bacSi.tuChoiBacSi(7L, "Lý do"));
            maLoi(403, () -> nguoiDung.khoaNguoiDung(3L, "Lý do"));
        }
        SecurityContextHolder.clearContext();
        maLoi(401, () -> bacSi.tuChoiBacSi(7L, "Lý do"));
        assertEquals("PENDING", trangThaiBacSi()); assertEquals(0, soAudit());
    }
    @Test void cacApiDocVaBoLocGiuContractTrenPostgres() {
        var ds = nguoiDung.layDanhSachNguoiDung("patient", "PATIENT", "ACTIVE", 0, 1);
        assertEquals(1, ds.tongPhanTu()); assertEquals(3L, ds.danhSachNguoiDung().get(0).maNguoiDung());
        assertEquals(0, nguoiDung.layDanhSachNguoiDung("%", null, null, 0, 10).tongPhanTu());
        assertEquals(1, bacSi.timKiemBacSi("doctor", "PENDING", null, 0, 10).getTotalElements());
        assertEquals(0, bacSi.timKiemBacSi("%", null, null, 0, 10).getTotalElements());
        bacSi.tuChoiBacSi(7L, "Lý do kiểm tra");
        assertTrue(bacSi.layBacSiChoDuyet().isEmpty());
        assertEquals(1, bacSi.timKiemBacSi(null, "REJECTED", null, 0, 10).getTotalElements());
        assertEquals("DOCTOR_REJECT", nhatKy.layDanhSachNhatKy(0, 10).danhSachNhatKy().get(0).hanhDong());
    }
    @RepeatedTest(3) void haiRejectDongThoiChiMotThanhCong() throws Exception {
        haiYeuCau(() -> bacSi.tuChoiBacSi(7L, "Lý do đồng thời"));
        assertEquals("REJECTED", trangThaiBacSi()); assertEquals(1, soAudit());
    }
    @RepeatedTest(3) void haiPatientLockDongThoiChiMotThanhCong() throws Exception {
        haiYeuCau(() -> nguoiDung.khoaNguoiDung(3L, "Lý do đồng thời"));
        assertFalse(patientHoatDong()); assertEquals(1, soAudit());
    }
    void haiYeuCau(Callable<?> thaoTac) throws Exception {
        ExecutorService luong = Executors.newFixedThreadPool(2);
        CountDownLatch sanSang = new CountDownLatch(2), batDau = new CountDownLatch(1);
        Callable<Integer> yeuCau = () -> {
            dangNhapAdmin(); sanSang.countDown();
            try {
                if (!batDau.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Hết thời gian chờ test");
                thaoTac.call(); return 200;
            } catch (ResponseStatusException loi) { return loi.getStatusCode().value(); }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            Future<Integer> a = luong.submit(yeuCau), b = luong.submit(yeuCau);
            assertTrue(sanSang.await(5, TimeUnit.SECONDS)); batDau.countDown();
            var ketQua = new java.util.ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
            ketQua.sort(Integer::compareTo); assertEquals(List.of(200, 409), ketQua);
        } finally { luong.shutdownNow(); assertTrue(luong.awaitTermination(5, TimeUnit.SECONDS)); }
    }
}
