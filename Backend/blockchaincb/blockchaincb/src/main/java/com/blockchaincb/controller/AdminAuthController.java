
package com.blockchaincb.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý thông tin xác thực của Admin.
 *
 * Chức năng:
 * - Kiểm tra trạng thái đăng nhập của Admin.
 * - Lấy thông tin tài khoản Admin đang đăng nhập.
 * - Lấy danh sách quyền của tài khoản Admin.
 *
 * Lưu ý:
 * - Spring Security xử lý đăng nhập và đăng xuất.
 * - Controller này không trực tiếp kiểm tra mật khẩu.
 * - Các API được bảo vệ bằng SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    /**
     * Lấy thông tin tài khoản Admin hiện tại.
     *
     * Đường dẫn: GET /api/admin/me
     *
     * @param xacThuc Thông tin xác thực do Spring Security cung cấp.
     * @return Thông tin tài khoản Admin đang đăng nhập.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> layThongTinAdmin(
            Authentication xacThuc) {

        // Lấy tên đăng nhập của Admin
        String tenDangNhap = xacThuc.getName();

        // Lấy danh sách quyền từ thông tin xác thực
        var danhSachQuyen = xacThuc.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // Chuẩn bị dữ liệu trả về cho Frontend
        Map<String, Object> duLieuPhanHoi = new LinkedHashMap<>();

        duLieuPhanHoi.put("tenDangNhap", tenDangNhap);
        duLieuPhanHoi.put("vaiTro", "ADMIN");
        duLieuPhanHoi.put("danhSachQuyen", danhSachQuyen);
        duLieuPhanHoi.put("daXacThuc", xacThuc.isAuthenticated());

        // Trả về thông tin Admin
        return ResponseEntity.ok(duLieuPhanHoi);
    }

    /**
     * Kiểm tra trạng thái đăng nhập của tài khoản Admin.
     *
     * Đường dẫn: GET /api/admin/trang-thai
     *
     * @return Trạng thái xác thực của tài khoản Admin.
     */
    @GetMapping("/trang-thai")
    public ResponseEntity<Map<String, Object>> kiemTraTrangThaiDangNhap() {

        // Lấy thông tin xác thực từ Spring Security
        Authentication xacThuc = SecurityContextHolder
                .getContext()
                .getAuthentication();

        // Kiểm tra phiên đăng nhập hiện tại
        boolean daDangNhap = xacThuc != null
                && xacThuc.isAuthenticated()
                && xacThuc.getAuthorities()
                    .stream()
                    .anyMatch(quyen ->
                        quyen.getAuthority().equals("ROLE_ADMIN")
                    );

        // Chuẩn bị dữ liệu phản hồi
        Map<String, Object> duLieuPhanHoi = new LinkedHashMap<>();

        duLieuPhanHoi.put("daDangNhap", daDangNhap);
        duLieuPhanHoi.put(
            "thongBao",
            daDangNhap
                ? "Admin đã đăng nhập thành công"
                : "Admin chưa đăng nhập"
        );

        return ResponseEntity.ok(duLieuPhanHoi);
    }
}
