package com.blockchaincb.service;

import java.util.List;
import com.blockchaincb.dto.DoctorResponse;
import com.blockchaincb.entity.Doctor;
import com.blockchaincb.repository.DoctorRepository;
import com.blockchaincb.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Đọc thông tin bác sĩ từ Supabase.
 * Các lệnh thay đổi trạng thái CHƯA kích hoạt vì thiếu giao dịch blockchain và audit.
 */
@Service
public class AdminDoctorService {
    private final DoctorRepository khoBacSi;
    private final UserRepository khoNguoiDung;
    private final String trangThaiChoDuyet;

    public AdminDoctorService(DoctorRepository khoBacSi, UserRepository khoNguoiDung,
            @Value("${app.doctor.pending-status:PENDING}") String trangThaiChoDuyet) {
        this.khoBacSi = khoBacSi;
        this.khoNguoiDung = khoNguoiDung;
        this.trangThaiChoDuyet = trangThaiChoDuyet;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> layDanhSachBacSi() {
        return khoBacSi.findAll().stream().map(this::chuyenThanhPhanHoi).toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> layBacSiChoDuyet() {
        return khoBacSi.findByTrangThaiPheDuyetIgnoreCase(trangThaiChoDuyet)
            .stream().map(this::chuyenThanhPhanHoi).toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse layChiTietBacSi(Long maBacSi) {
        if (maBacSi == null || maBacSi <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bác sĩ không hợp lệ");
        }
        Doctor bacSi = khoBacSi.findById(maBacSi)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bác sĩ"));
        return chuyenThanhPhanHoi(bacSi);
    }

    public DoctorResponse duyetBacSi(Long maBacSi) {
        throw chuaHoTro("Duyệt bác sĩ cần xác nhận cấp DOCTOR_ROLE trên blockchain");
    }

    public DoctorResponse tuChoiBacSi(Long maBacSi, String lyDo) {
        kiemTraLyDo(lyDo);
        throw chuaHoTro("Từ chối bác sĩ cần thống nhất mã trạng thái và nhật ký quản trị");
    }

    public DoctorResponse khoaBacSi(Long maBacSi, String lyDo) {
        kiemTraLyDo(lyDo);
        throw chuaHoTro("Khóa bác sĩ cần thu hồi quyền blockchain và ghi nhật ký");
    }

    public DoctorResponse moKhoaBacSi(Long maBacSi) {
        throw chuaHoTro("Mở khóa bác sĩ cần xác nhận cấp lại quyền blockchain");
    }

    private void kiemTraLyDo(String lyDo) {
        if (lyDo == null || lyDo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do không được để trống");
        }
    }

    private ResponseStatusException chuaHoTro(String thongBao) {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, thongBao);
    }

    private DoctorResponse chuyenThanhPhanHoi(Doctor bacSi) {
        String diaChiVi = khoNguoiDung.findById(bacSi.getMaNguoiDung())
            .map(nguoiDung -> nguoiDung.getDiaChiVi()).orElse(null);
        // Chưa có Entity specialties: hiển thị mã thay vì tự đoán tên chuyên khoa.
        String chuyenKhoa = "Mã chuyên khoa: " + bacSi.getMaChuyenKhoa();
        return new DoctorResponse(bacSi.getMaBacSi(), bacSi.getHoTen(), bacSi.getEmail(),
            bacSi.getSoGiayPhep(), bacSi.getBenhVien(), chuyenKhoa,
            diaChiVi, bacSi.getTrangThaiTaiKhoan(), bacSi.getTrangThaiPheDuyet());
    }
}
