
package com.blockchaincb.controller;

import com.blockchaincb.dto.DoctorActionRequest;
import com.blockchaincb.dto.DoctorResponse;
import com.blockchaincb.service.AdminDoctorService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý tài khoản bác sĩ dành cho Admin.
 *
 * Chức năng:
 * - Xem danh sách bác sĩ.
 * - Xem danh sách bác sĩ đang chờ duyệt.
 * - Xem thông tin chi tiết bác sĩ.
 * - Duyệt hoặc từ chối đăng ký bác sĩ.
 * - Khóa hoặc mở khóa tài khoản bác sĩ.
 *
 * Lưu ý:
 * - Chỉ tài khoản ADMIN được phép truy cập.
 * - Controller không trực tiếp thao tác cơ sở dữ liệu.
 * - Các nghiệp vụ được xử lý tại AdminDoctorService.
 */
@RestController
@RequestMapping("/api/admin/doctors")
public class AdminDoctorController {

    // Service xử lý nghiệp vụ quản lý bác sĩ
    private final AdminDoctorService dichVuQuanLyBacSi;

    /**
     * Khởi tạo Controller bằng Dependency Injection.
     *
     * @param dichVuQuanLyBacSi Service quản lý bác sĩ.
     */
    public AdminDoctorController(
            AdminDoctorService dichVuQuanLyBacSi) {

        this.dichVuQuanLyBacSi = dichVuQuanLyBacSi;
    }

    /**
     * Lấy danh sách tất cả bác sĩ.
     *
     * GET /api/admin/doctors
     */
    @GetMapping
    public ResponseEntity<List<DoctorResponse>> layDanhSachBacSi() {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.layDanhSachBacSi()
        );
    }

    /**
     * Lấy danh sách bác sĩ đang chờ phê duyệt.
     *
     * GET /api/admin/doctors/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<DoctorResponse>> layBacSiChoDuyet() {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.layBacSiChoDuyet()
        );
    }

    /**
     * Lấy thông tin chi tiết của một bác sĩ.
     *
     * GET /api/admin/doctors/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> layChiTietBacSi(
            @PathVariable("id") Long maBacSi) {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.layChiTietBacSi(maBacSi)
        );
    }

    /**
     * Phê duyệt tài khoản bác sĩ.
     *
     * POST /api/admin/doctors/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<DoctorResponse> duyetBacSi(
            @PathVariable("id") Long maBacSi) {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.duyetBacSi(maBacSi)
        );
    }

    /**
     * Từ chối yêu cầu đăng ký bác sĩ.
     *
     * POST /api/admin/doctors/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<DoctorResponse> tuChoiBacSi(
            @PathVariable("id") Long maBacSi,
            @RequestBody DoctorActionRequest yeuCau) {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.tuChoiBacSi(
                maBacSi,
                yeuCau.lyDo()
            )
        );
    }

    /**
     * Khóa tài khoản bác sĩ.
     *
     * POST /api/admin/doctors/{id}/suspend
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<DoctorResponse> khoaBacSi(
            @PathVariable("id") Long maBacSi,
            @RequestBody DoctorActionRequest yeuCau) {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.khoaBacSi(
                maBacSi,
                yeuCau.lyDo()
            )
        );
    }

    /**
     * Mở khóa tài khoản bác sĩ.
     *
     * POST /api/admin/doctors/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<DoctorResponse> moKhoaBacSi(
            @PathVariable("id") Long maBacSi) {

        return ResponseEntity.ok(
            dichVuQuanLyBacSi.moKhoaBacSi(maBacSi)
        );
    }
}
