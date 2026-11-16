package com.blockchaincb.service;

import com.blockchaincb.blockchain.BlockchainBacSiChuaTichHop;
import com.blockchaincb.entity.Doctor;
import com.blockchaincb.entity.User;
import com.blockchaincb.repository.AdminDoctorRepository;
import com.blockchaincb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDoctorServiceTest {
    AdminDoctorRepository kho;
    UserRepository nguoiDung;
    AdminAuditLogService nhatKy;
    AdminDoctorService dichVu;
    Doctor bacSi;
    User taiKhoan;

    @BeforeEach void chuanBi() {
        kho = mock(AdminDoctorRepository.class);
        nguoiDung = mock(UserRepository.class);
        nhatKy = mock(AdminAuditLogService.class);
        dichVu = new AdminDoctorService(kho, nguoiDung, nhatKy, new BlockchainBacSiChuaTichHop());
        bacSi = new Doctor();
        ReflectionTestUtils.setField(bacSi, "maBacSi", 7L);
        ReflectionTestUtils.setField(bacSi, "maNguoiDung", 2L);
        ReflectionTestUtils.setField(bacSi, "maChuyenKhoa", 1L);
        ReflectionTestUtils.setField(bacSi, "trangThaiPheDuyet", "PENDING");
        ReflectionTestUtils.setField(bacSi, "soGiayPhep", "GP-TEST");
        ReflectionTestUtils.setField(bacSi, "benhVien", "Cơ sở thử nghiệm");
        ReflectionTestUtils.setField(bacSi, "hoTen", "Bác sĩ thử");
        ReflectionTestUtils.setField(bacSi, "email", "doctor@example.test");
        ReflectionTestUtils.setField(bacSi, "trangThaiTaiKhoan", "GIA_TRI_CUA_NHOM");
        taiKhoan = new User(); taiKhoan.setVaiTro("DOCTOR"); taiKhoan.setDangHoatDong(true);
        when(nhatKy.layMaAdminDangNhap()).thenReturn(1L);
        when(kho.layDeCapNhat(7L)).thenReturn(Optional.of(bacSi));
        when(kho.findById(7L)).thenReturn(Optional.of(bacSi));
        when(nguoiDung.layDeCapNhat(2L)).thenReturn(Optional.of(taiKhoan));
        when(nguoiDung.findById(2L)).thenReturn(Optional.of(taiKhoan));
    }

    private void maLoi(HttpStatus ma, Runnable thaoTac) {
        assertEquals(ma, assertThrows(ResponseStatusException.class, thaoTac::run).getStatusCode());
    }

    @Test void tuChoiChiCapNhatSauKhiKhoaVaGhiDungAudit() {
        when(kho.tuChoiHoSoChoDuyet(7L)).thenAnswer(goi -> {
            ReflectionTestUtils.setField(bacSi, "trangThaiPheDuyet", "REJECTED"); return 1;
        });
        assertEquals("REJECTED", dichVu.tuChoiBacSi(7L, "  Hồ sơ không hợp lệ  ").trangThaiPheDuyet());
        var thuTu = inOrder(kho, nguoiDung, nhatKy);
        thuTu.verify(nhatKy).layMaAdminDangNhap();
        thuTu.verify(kho).layDeCapNhat(7L);
        thuTu.verify(nguoiDung).layDeCapNhat(2L);
        thuTu.verify(kho).tuChoiHoSoChoDuyet(7L);
        thuTu.verify(nhatKy).ghiNhatKy(AdminAuditLogService.HanhDongQuanTri.DOCTOR_REJECT,
                AdminAuditLogService.LoaiDoiTuong.DOCTOR, 7L, "Từ chối hồ sơ bác sĩ #7. Lý do: Hồ sơ không hợp lệ");
        assertNull(bacSi.getMaNguoiPheDuyet()); assertNull(bacSi.getNgayPheDuyet());
    }
    @ParameterizedTest @ValueSource(strings = {"APPROVED", "REJECTED", "UNKNOWN"})
    void chiTuChoiPending(String trangThai) {
        ReflectionTestUtils.setField(bacSi, "trangThaiPheDuyet", trangThai);
        maLoi(HttpStatus.CONFLICT, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
        verify(kho, never()).tuChoiHoSoChoDuyet(anyLong());
        verify(nhatKy, never()).ghiNhatKy(any(), any(), anyLong(), anyString());
    }
    @Test void khongTimThayTra404() {
        maLoi(HttpStatus.NOT_FOUND, () -> dichVu.tuChoiBacSi(99L, "Lý do"));
    }
    @Test void lyDoKhongHopLeTra400TruocKhiTruyVan() {
        for (String lyDo : new String[] {null, "", "   ", "a".repeat(351)}) {
            maLoi(HttpStatus.BAD_REQUEST, () -> dichVu.tuChoiBacSi(7L, lyDo));
        }
        verifyNoInteractions(kho, nguoiDung);
    }
    @Test void maKhongHopLeTra400() {
        for (Long ma : new Long[] {null, 0L, -1L}) maLoi(HttpStatus.BAD_REQUEST, () -> dichVu.tuChoiBacSi(ma, "Lý do"));
    }
    @Test void taiKhoanLienKetPhaiLaDoctor() {
        taiKhoan.setVaiTro("PATIENT");
        maLoi(HttpStatus.CONFLICT, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
        verify(kho, never()).tuChoiHoSoChoDuyet(anyLong());
    }
    @Test void khongCoTaiKhoanLienKetKhongTuChoi() {
        when(nguoiDung.layDeCapNhat(2L)).thenReturn(Optional.empty());
        maLoi(HttpStatus.CONFLICT, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
    }
    @Test void capNhatKhongDungMotDongKhongGhiAudit() {
        when(kho.tuChoiHoSoChoDuyet(7L)).thenReturn(0);
        maLoi(HttpStatus.CONFLICT, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
        verify(nhatKy, never()).ghiNhatKy(any(), any(), anyLong(), anyString());
    }
    @Test void loiAuditPhaiTruyenRaDeRollback() {
        when(kho.tuChoiHoSoChoDuyet(7L)).thenReturn(1);
        when(nhatKy.ghiNhatKy(any(), any(), anyLong(), anyString())).thenThrow(new IllegalStateException("Lỗi audit thử nghiệm"));
        assertThrows(IllegalStateException.class, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
    }
    @Test void phaiXacMinhAdminTruocKhiCapNhat() {
        when(nhatKy.layMaAdminDangNhap()).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        maLoi(HttpStatus.FORBIDDEN, () -> dichVu.tuChoiBacSi(7L, "Lý do"));
        verifyNoInteractions(kho);
    }
    @Test void approveHopLeVan501KhongGhiDbHoacAudit() {
        maLoi(HttpStatus.NOT_IMPLEMENTED, () -> dichVu.duyetBacSi(7L));
        verify(kho, never()).tuChoiHoSoChoDuyet(anyLong());
        verify(nhatKy, never()).ghiNhatKy(any(), any(), anyLong(), anyString());
    }
    @Test void approveThieuGiayPhepTra409() {
        ReflectionTestUtils.setField(bacSi, "soGiayPhep", " ");
        maLoi(HttpStatus.CONFLICT, () -> dichVu.duyetBacSi(7L));
    }
    @Test void suspendReactivateKhongTuDatGiaTriStatus() {
        ReflectionTestUtils.setField(bacSi, "trangThaiPheDuyet", "APPROVED");
        maLoi(HttpStatus.NOT_IMPLEMENTED, () -> dichVu.khoaBacSi(7L, "Lý do"));
        maLoi(HttpStatus.NOT_IMPLEMENTED, () -> dichVu.moKhoaBacSi(7L));
        assertEquals("GIA_TRI_CUA_NHOM", bacSi.getTrangThaiTaiKhoan());
        verify(nhatKy, never()).ghiNhatKy(any(), any(), anyLong(), anyString());
    }
}
