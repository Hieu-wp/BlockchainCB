package com.blockchaincb.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import com.blockchaincb.repository.UserRepository;
import com.blockchaincb.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thống kê dữ liệu thật từ public.users và public.doctors. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository khoNguoiDung;
    private final DoctorRepository khoBacSi;
    private final String trangThaiChoDuyet;

    public AdminController(UserRepository khoNguoiDung, DoctorRepository khoBacSi,
            @Value("${app.doctor.pending-status:PENDING}") String trangThaiChoDuyet) {
        this.khoNguoiDung = khoNguoiDung;
        this.khoBacSi = khoBacSi;
        this.trangThaiChoDuyet = trangThaiChoDuyet;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> kiemTraHoatDong() {
        return ResponseEntity.ok(Map.of("trangThai", "thanhCong", "thongBao", "Admin API đang hoạt động"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> layThongKeTongQuan() {
        Map<String, Object> soLieu = new LinkedHashMap<>();
        soLieu.put("tongNguoiDung", khoNguoiDung.count());
        soLieu.put("tongBenhNhan", khoNguoiDung.countByVaiTroIgnoreCase("PATIENT"));
        soLieu.put("tongTaiKhoanVaiTroDoctor", khoNguoiDung.countByVaiTroIgnoreCase("DOCTOR"));
        soLieu.put("tongHoSoBacSi", khoBacSi.count());
        soLieu.put("bacSiChoDuyet", khoBacSi.countByTrangThaiPheDuyetIgnoreCase(trangThaiChoDuyet));
        soLieu.put("taiKhoanDangHoatDong", khoNguoiDung.countByDangHoatDong(true));
        // Chưa có dữ liệu transaction/audit để tránh trả số giả.
        return ResponseEntity.ok(soLieu);
    }
}
