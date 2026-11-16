
package com.blockchaincb.controller;

import com.blockchaincb.dto.AdminUserActionRequest;
import com.blockchaincb.dto.AdminUserPageResponse;
import com.blockchaincb.dto.AdminUserResponse;
import com.blockchaincb.service.AdminUserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý người dùng dành riêng cho Admin.
 *
 * Chức năng:
 * 1. Xem danh sách người dùng.
 * 2. Tìm kiếm người dùng theo họ tên và email.
 * 3. Lọc người dùng theo vai trò.
 * 4. Lọc người dùng theo trạng thái tài khoản.
 * 5. Phân trang danh sách người dùng.
 * 6. Xem chi tiết người dùng.
 * 7. Khóa tài khoản PATIENT.
 * 8. Mở khóa tài khoản PATIENT.
 *
 * Quy tắc bảo mật:
 * - Chỉ ROLE_ADMIN được truy cập các API.
 * - Không cho phép Admin khóa tài khoản Admin.
 * - Chưa hỗ trợ thay đổi trạng thái DOCTOR.
 * - Mỗi thao tác khóa/mở khóa thành công phải ghi nhật ký.
 *
 * Các quy tắc nghiệp vụ được xử lý tại AdminUserService.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    // Dịch vụ quản lý người dùng dành cho Admin.
    private final AdminUserService dichVuQuanLyNguoiDung;

    /**
     * Khởi tạo Controller thông qua Dependency Injection.
     */
    public AdminUserController(
            AdminUserService dichVuQuanLyNguoiDung) {

        this.dichVuQuanLyNguoiDung = dichVuQuanLyNguoiDung;
    }

    /**
     * Lấy danh sách người dùng, hỗ trợ tìm kiếm,
     * lọc theo vai trò, trạng thái và phân trang.
     *
     * GET /api/admin/users
     *
     * Các tham số:
     *
     * tuKhoa:
     * - Tìm theo họ tên hoặc email.
     *
     * vaiTro:
     * - ADMIN, DOCTOR, PATIENT.
     *
     * trangThai:
     * - ACTIVE, INACTIVE.
     *
     * trang:
     * - Số trang bắt đầu từ 0.
     *
     * kichThuoc:
     * - Số bản ghi mỗi trang từ 1 đến 100.
     *
     * Ví dụ:
     * GET /api/admin/users?trang=0&kichThuoc=10
     * GET /api/admin/users?vaiTro=PATIENT
     * GET /api/admin/users?trangThai=ACTIVE
     * GET /api/admin/users?tuKhoa=admin
     */
    @GetMapping
    public ResponseEntity<AdminUserPageResponse> layDanhSachNguoiDung(
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(required = false) String vaiTro,
            @RequestParam(required = false) String trangThai,
            @RequestParam(defaultValue = "0") int trang,
            @RequestParam(defaultValue = "10") int kichThuoc) {

        AdminUserPageResponse ketQua =
                dichVuQuanLyNguoiDung.layDanhSachNguoiDung(
                        tuKhoa,
                        vaiTro,
                        trangThai,
                        trang,
                        kichThuoc
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Lấy thông tin chi tiết một người dùng.
     *
     * GET /api/admin/users/{id}
     *
     * Ví dụ:
     * GET /api/admin/users/1
     *
     * Không trả về password_hash hoặc dữ liệu bệnh án.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> layChiTietNguoiDung(
            @PathVariable("id") Long maNguoiDung) {

        AdminUserResponse ketQua =
                dichVuQuanLyNguoiDung.layChiTietNguoiDung(
                        maNguoiDung
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Khóa tài khoản người dùng.
     *
     * POST /api/admin/users/{id}/lock
     *
     * Request body:
     * {
     *   "lyDo": "Vi phạm quy định sử dụng hệ thống"
     * }
     *
     * Quy trình xử lý trong Service:
     * - Xác thực Admin đang đăng nhập.
     * - Chỉ cho phép khóa PATIENT.
     * - Kiểm tra trạng thái hiện tại.
     * - Cập nhật users.enabled = FALSE.
     * - Ghi nhật ký USER_LOCK.
     * - Commit hoặc rollback giao dịch PostgreSQL.
     */
    @PostMapping("/{id}/lock")
    public ResponseEntity<AdminUserResponse> khoaNguoiDung(
            @PathVariable("id") Long maNguoiDung,
            @Valid @RequestBody AdminUserActionRequest yeuCau) {

        AdminUserResponse ketQua =
                dichVuQuanLyNguoiDung.khoaNguoiDung(
                        maNguoiDung,
                        yeuCau.lyDo()
                );

        return ResponseEntity.ok(ketQua);
    }

    /**
     * Mở khóa tài khoản người dùng.
     *
     * POST /api/admin/users/{id}/unlock
     *
     * Không cần request body.
     *
     * Quy trình xử lý trong Service:
     * - Xác thực Admin đang đăng nhập.
     * - Chỉ cho phép mở khóa PATIENT.
     * - Kiểm tra trạng thái hiện tại.
     * - Cập nhật users.enabled = TRUE.
     * - Ghi nhật ký USER_UNLOCK.
     * - Commit hoặc rollback giao dịch PostgreSQL.
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<AdminUserResponse> moKhoaNguoiDung(
            @PathVariable("id") Long maNguoiDung) {

        AdminUserResponse ketQua =
                dichVuQuanLyNguoiDung.moKhoaNguoiDung(
                        maNguoiDung
                );

        return ResponseEntity.ok(ketQua);
    }
}
