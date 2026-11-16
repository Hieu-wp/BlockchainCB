package com.blockchaincb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    NamedParameterJdbcTemplate jdbc;
    AdminAuditLogService nhatKy;
    AdminUserService dichVu;
    String vaiTro;
    boolean hoatDong;
    boolean tonTai;

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"})
    void chuanBi() {
        jdbc = mock(NamedParameterJdbcTemplate.class); nhatKy = mock(AdminAuditLogService.class);
        dichVu = new AdminUserService(jdbc, nhatKy);
        vaiTro = "PATIENT"; hoatDong = true; tonTai = true;
        when(nhatKy.layMaAdminDangNhap()).thenReturn(1L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenAnswer(goi -> {
            if (!tonTai) return List.of();
            ResultSet dong = mock(ResultSet.class);
            when(dong.getLong("id")).thenReturn(3L); when(dong.getString("role")).thenReturn(vaiTro);
            when(dong.getBoolean("enabled")).thenReturn(hoatDong);
            when(dong.getString("full_name")).thenReturn("Patient test");
            when(dong.getString("email")).thenReturn("patient@example.test");
            when(dong.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
            return List.of(((RowMapper<?>) goi.getArgument(2)).mapRow(dong, 0));
        });
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenAnswer(goi -> {
            hoatDong = goi.<String>getArgument(0).contains("SET enabled = TRUE"); return 1;
        });
    }
    void maLoi(int ma, Runnable thaoTac) {
        assertEquals(ma, assertThrows(ResponseStatusException.class, thaoTac::run).getStatusCode().value());
        verify(nhatKy, never()).ghiNhatKy(any(), any(), anyLong(), anyString());
    }
    @Test void patientLockUnlockGoiDungAudit() {
        assertEquals("INACTIVE", dichVu.khoaNguoiDung(3L, "  Lý do  ").trangThai());
        verify(nhatKy).ghiNhatKy(AdminAuditLogService.HanhDongQuanTri.USER_LOCK,
                AdminAuditLogService.LoaiDoiTuong.USER, 3L, "Khóa tài khoản người dùng #3. Lý do: Lý do");
        assertEquals("ACTIVE", dichVu.moKhoaNguoiDung(3L).trangThai());
        verify(nhatKy).ghiNhatKy(AdminAuditLogService.HanhDongQuanTri.USER_UNLOCK,
                AdminAuditLogService.LoaiDoiTuong.USER, 3L, "Mở khóa tài khoản người dùng #3");
    }
    @Test void khongKhoaAdmin() { vaiTro = "ADMIN"; maLoi(403, () -> dichVu.khoaNguoiDung(3L, "Lý do")); }
    @Test void khongKhoaDoctor() { vaiTro = "DOCTOR"; maLoi(409, () -> dichVu.khoaNguoiDung(3L, "Lý do")); }
    @Test void khongTuKhoaAdmin() { maLoi(403, () -> dichVu.khoaNguoiDung(1L, "Lý do")); }
    @Test void khongKhoaTaiKhoanKhongTonTai() { tonTai = false; maLoi(404, () -> dichVu.khoaNguoiDung(3L, "Lý do")); }
    @Test void khongKhoaLaiTaiKhoanInActive() { hoatDong = false; maLoi(409, () -> dichVu.khoaNguoiDung(3L, "Lý do")); }
    @Test void khongMoKhoaLaiTaiKhoanActive() { maLoi(409, () -> dichVu.moKhoaNguoiDung(3L)); }
    @Test void lyDoRongQuaDaiBiTuChoi() {
        for (String lyDo : new String[] {null, "", "   ", "a".repeat(351)}) maLoi(400, () -> dichVu.khoaNguoiDung(3L, lyDo));
        verifyNoInteractions(jdbc);
    }
    @Test void capNhatKhongDuocMotDongKhongGhiAudit() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        maLoi(409, () -> dichVu.khoaNguoiDung(3L, "Lý do"));
    }
    @Test void loiAuditKhongBiNuot() {
        when(nhatKy.ghiNhatKy(any(), any(), anyLong(), anyString())).thenThrow(new IllegalStateException("Lỗi audit thử nghiệm"));
        assertThrows(IllegalStateException.class, () -> dichVu.khoaNguoiDung(3L, "Lý do"));
        // Test mock chỉ xác minh exception; rollback DB thuộc PostgresAdminIT.
    }
}
