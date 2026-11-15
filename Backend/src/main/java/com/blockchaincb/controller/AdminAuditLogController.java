
package com.blockchaincb.controller;

import com.blockchaincb.service.AdminAuditLogService;
import com.blockchaincb.service.AdminAuditLogService.TrangNhatKyQuanTri;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xem nhật ký quản trị.
 *
 * Chỉ tài khoản có ROLE_ADMIN mới được truy cập.
 *
 * Chức năng:
 * - Xem danh sách nhật ký.
 * - Phân trang nhật ký.
 *
 * Không cung cấp API tạo, sửa hoặc xóa nhật ký.
 * Nhật ký được ghi bởi các Service nghiệp vụ.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogService dichVuNhatKy;

    /**
     * Khởi tạo Controller.
     */
    public AdminAuditLogController(
            AdminAuditLogService dichVuNhatKy) {

        this.dichVuNhatKy = dichVuNhatKy;
    }

    /**
     * Lấy danh sách nhật ký quản trị.
     *
     * GET /api/admin/audit-logs
     *
     * Ví dụ:
     * /api/admin/audit-logs?trang=0&kichThuoc=10
     */
    @GetMapping
    public ResponseEntity<TrangNhatKyQuanTri> layDanhSachNhatKy(
            @RequestParam(defaultValue = "0") int trang,
            @RequestParam(defaultValue = "10") int kichThuoc) {

        TrangNhatKyQuanTri ketQua =
                dichVuNhatKy.layDanhSachNhatKy(
                        trang,
                        kichThuoc
                );

        return ResponseEntity.ok(ketQua);
    }
}
