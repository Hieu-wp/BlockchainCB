
package com.blockchaincb.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý các chức năng tổng quan của Admin.
 *
 * Chức năng:
 * - Kiểm tra trạng thái hoạt động của API Admin.
 * - Cung cấp dữ liệu thống kê cho Dashboard.
 *
 * Lưu ý:
 * - Các API được bảo vệ bởi Spring Security.
 * - Dữ liệu thống kê hiện tại là dữ liệu mẫu.
 * - Sau này sẽ kết nối Service và Database.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Kiểm tra trạng thái hoạt động của Admin API.
     *
     * Đường dẫn: GET /api/admin/health
     *
     * @return Thông báo trạng thái hoạt động.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> kiemTraHoatDong() {

        // Tạo dữ liệu phản hồi
        Map<String, String> duLieuPhanHoi = new LinkedHashMap<>();

        duLieuPhanHoi.put("trangThai", "thanhCong");
        duLieuPhanHoi.put("thongBao", "Admin API đang hoạt động");

        return ResponseEntity.ok(duLieuPhanHoi);
    }

    /**
     * Lấy dữ liệu thống kê tổng quan hệ thống.
     *
     * Đường dẫn: GET /api/admin/dashboard
     *
     * @return Dữ liệu thống kê Dashboard.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> layThongKeTongQuan() {

        // Khởi tạo dữ liệu thống kê
        Map<String, Object> thongKe = new LinkedHashMap<>();

        // Thống kê tài khoản người dùng
        thongKe.put("tongNguoiDung", 0);
        thongKe.put("tongBenhNhan", 0);
        thongKe.put("tongBacSi", 0);

        // Thống kê trạng thái bác sĩ
        thongKe.put("bacSiChoDuyet", 0);
        thongKe.put("bacSiDaDuyet", 0);
        thongKe.put("bacSiBiTuChoi", 0);
        thongKe.put("bacSiBiKhoa", 0);

        // Thống kê hồ sơ khám bệnh
        thongKe.put("tongHoSoKhamBenh", 0);

        // Thống kê giao dịch Blockchain
        thongKe.put("tongGiaoDich", 0);
        thongKe.put("giaoDichThanhCong", 0);
        thongKe.put("giaoDichThatBai", 0);

        return ResponseEntity.ok(thongKe);
    }
}
