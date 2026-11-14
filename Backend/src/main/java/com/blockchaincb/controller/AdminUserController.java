
package com.blockchaincb.controller;

import com.blockchaincb.dto.AdminUserPageResponse;
import com.blockchaincb.dto.AdminUserResponse;
import com.blockchaincb.service.AdminUserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý danh sách người dùng dành cho Admin.
 *
 * Chức năng:
 * - Xem danh sách người dùng toàn hệ thống.
 * - Tìm kiếm người dùng theo từ khóa.
 * - Lọc người dùng theo vai trò và trạng thái.
 * - Phân trang danh sách người dùng.
 * - Xem thông tin chi tiết của một tài khoản.
 *
 * Lưu ý:
 * - Chỉ tài khoản có quyền ADMIN được truy cập.
 * - Không trả về mật khẩu hoặc dữ liệu bệnh án.
 * - Controller không trực tiếp truy vấn Database.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    // Service xử lý nghiệp vụ quản lý người dùng
    private final AdminUserService dichVuQuanLyNguoiDung;

    /**
     * Khởi tạo Controller bằng Dependency Injection.
     *
     * @param dichVuQuanLyNguoiDung Service quản lý người dùng.
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
     * @param tuKhoa Từ khóa tìm kiếm, có thể để trống.
     * @param vaiTro Vai trò ADMIN, DOCTOR hoặc PATIENT.
     * @param trangThai Trạng thái tài khoản.
     * @param trang Chỉ số trang, bắt đầu từ 0.
     * @param kichThuoc Số người dùng trên một trang.
     * @return Danh sách người dùng và thông tin phân trang.
     */
    @GetMapping
    public ResponseEntity<AdminUserPageResponse> layDanhSachNguoiDung(
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(required = false) String vaiTro,
            @RequestParam(required = false) String trangThai,
            @RequestParam(defaultValue = "0") int trang,
            @RequestParam(defaultValue = "10") int kichThuoc) {

        return ResponseEntity.ok(
            dichVuQuanLyNguoiDung.layDanhSachNguoiDung(
                tuKhoa,
                vaiTro,
                trangThai,
                trang,
                kichThuoc
            )
        );
    }

    /**
     * Lấy thông tin chi tiết tài khoản người dùng.
     *
     * GET /api/admin/users/{id}
     *
     * @param maNguoiDung Mã định danh người dùng.
     * @return Thông tin tài khoản người dùng.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> layChiTietNguoiDung(
            @PathVariable("id") Long maNguoiDung) {

        return ResponseEntity.ok(
            dichVuQuanLyNguoiDung.layChiTietNguoiDung(
                maNguoiDung
            )
        );
    }
}
