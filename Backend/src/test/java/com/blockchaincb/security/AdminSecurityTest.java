package com.blockchaincb.security;

import com.blockchaincb.controller.*;
import com.blockchaincb.dto.*;
import com.blockchaincb.repository.*;
import com.blockchaincb.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import com.blockchaincb.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = {AdminController.class, AdminAuthController.class, AdminDoctorController.class,
        AdminUserController.class, AdminAuditLogController.class}, properties = "spring.config.location=classpath:application-test.properties")
@Import({SecurityConfig.class, AdminAuthService.class})
class AdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminDoctorService bacSi;
    @MockitoBean AdminUserService nguoiDung;
    @MockitoBean AdminAuditLogService nhatKy;
    @MockitoBean UserRepository khoNguoiDung;
    @MockitoBean DoctorRepository khoBacSi;

    @Test void formLoginSessionMeVaLogoutThucTrongSecurityFilter() throws Exception {
        User admin = new User(); admin.setEmail("admin@example.test"); admin.setVaiTro("ADMIN");
        admin.setDangHoatDong(true); admin.setMatKhauMaHoa(new BCryptPasswordEncoder().encode("test-password"));
        when(khoNguoiDung.findByEmailIgnoreCase("admin@example.test")).thenReturn(java.util.Optional.of(admin));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/login").with(csrf()).param("username", "admin@example.test").param("password", "wrong-test-password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        var dangNhap = mvc.perform(post("/login").with(csrf()).param("username", "admin@example.test").param("password", "test-password"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession phien = (MockHttpSession) dangNhap.getRequest().getSession(false);
        assertNotNull(phien);
        mvc.perform(get("/api/admin/me").session(phien)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vaiTro").value("ADMIN"))
                .andExpect(jsonPath("$.danhSachQuyen", org.hamcrest.Matchers.hasItem("ROLE_ADMIN")))
                .andExpect(jsonPath("$.daXacThuc").value(true));
        mvc.perform(post("/logout").session(phien).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        assertTrue(phien.isInvalid());
        mvc.perform(get("/api/admin/me").accept("application/json")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test void chuaDangNhapKhongDocDuocAdmin() throws Exception {
        mvc.perform(get("/api/admin/me").accept("application/json")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(nguoiDung, bacSi, nhatKy);
    }
    @ParameterizedTest @ValueSource(strings = {"PATIENT", "DOCTOR"})
    void cacVaiTroKhacKhongDocAdmin(String vaiTro) throws Exception {
        for (String path : new String[] {"me", "health", "dashboard", "users", "doctors", "audit-logs"}) {
            mvc.perform(get("/api/admin/" + path).with(user("test@example.test").roles(vaiTro))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(nguoiDung, bacSi, nhatKy);
    }
    @Test void jsonMeGiuDungContract() throws Exception {
        mvc.perform(get("/api/admin/me").with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenDangNhap").value("admin@example.test"))
                .andExpect(jsonPath("$.vaiTro").value("ADMIN"))
                .andExpect(jsonPath("$.daXacThuc").value(true))
                .andExpect(jsonPath("$.danhSachQuyen[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.dangNhapThanhCong").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "suspend", "reactivate"})
    void postBacSiThieuCsrfBiChan(String hanhDong) throws Exception {
        mvc.perform(post("/api/admin/doctors/7/" + hanhDong).with(user("admin@example.test").roles("ADMIN"))
                .contentType("application/json").content("{\"lyDo\":\"Lý do thử nghiệm\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bacSi);
    }
    @ParameterizedTest @ValueSource(strings = {"PATIENT", "DOCTOR"})
    void postBacSiCoCsrfVanCanAdmin(String vaiTro) throws Exception {
        mvc.perform(post("/api/admin/doctors/7/reject").with(user("test@example.test").roles(vaiTro)).with(csrf())
                .contentType("application/json").content("{\"lyDo\":\"Lý do thử nghiệm\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bacSi);
    }
    @Test void rejectAdminCoCsrfGiuDungDtoKhongLoMatKhau() throws Exception {
        when(bacSi.tuChoiBacSi(7L, "Lý do thử nghiệm")).thenReturn(new DoctorResponse(7L, "Bác sĩ", "doctor@example.test", "GP", "Bệnh viện", "Mã chuyên khoa: 1", null, "PENDING", "REJECTED"));
        mvc.perform(post("/api/admin/doctors/7/reject").with(user("admin@example.test").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"lyDo\":\"Lý do thử nghiệm\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trangThaiPheDuyet").value("REJECTED"))
                .andExpect(jsonPath("$.soGiayPhep").value("GP")).andExpect(jsonPath("$.noiCongTac").value("Bệnh viện"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.matKhauCu").doesNotExist());
    }
    @Test void rejectLyDoRongHoacQuaDaiTra400() throws Exception {
        for (String lyDo : new String[] {"", "   ", "a".repeat(351)}) {
            mvc.perform(post("/api/admin/doctors/7/reject").with(user("admin@example.test").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content("{\"lyDo\":\"" + lyDo + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(bacSi);
    }
    @Test void loiNghiepVuGiuMaHttpVaChiTietChoFrontend() throws Exception {
        for (int ma : new int[] {400, 401, 403, 404, 409, 501}) {
            doThrow(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.valueOf(ma), "Thông báo nghiệp vụ thử nghiệm")).when(bacSi).duyetBacSi(7L);
            mvc.perform(post("/api/admin/doctors/7/approve").with(user("admin@example.test").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is(ma)).andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.detail").value("Thông báo nghiệp vụ thử nghiệm"));
        }
    }
    @Test void khoaPatientCanCsrfVaLyDoHopLe() throws Exception {
        mvc.perform(post("/api/admin/users/3/lock").with(user("admin@example.test").roles("ADMIN"))
                .contentType("application/json").content("{\"lyDo\":\"Lý do\"}")).andExpect(status().isForbidden());
        for (String lyDo : new String[] {" ", "a".repeat(351)}) {
            mvc.perform(post("/api/admin/users/3/lock").with(user("admin@example.test").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content("{\"lyDo\":\"" + lyDo + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(nguoiDung);
    }
}
