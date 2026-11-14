
package com.blockchaincb.service;

import com.blockchaincb.dto.AdminUserPageResponse;
import com.blockchaincb.dto.AdminUserResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service xử lý nghiệp vụ quản lý người dùng dành cho Admin.
 *
 * Chức năng dự kiến:
 * - Lấy danh sách người dùng.
 * - Tìm kiếm, lọc và phân trang tài khoản.
 * - Lấy thông tin chi tiết người dùng.
 *
 * Hiện tại chưa kết nối Database và UserRepository.
 * Các chức năng chưa triển khai sẽ trả HTTP 501.
 */
@Service
public class AdminUserService {

    /**
     * Lấy danh sách người dùng theo điều kiện tìm kiếm.
     *
     * @param tuKhoa Từ khóa tìm kiếm.
     * @param vaiTro Vai trò người dùng.
     * @param trangThai Trạng thái tài khoản.
     * @param trang Chỉ số trang.
     * @param kichThuoc Số phần tử trên một trang.
     * @return Dữ liệu danh sách người dùng có phân trang.
     */
    public AdminUserPageResponse layDanhSachNguoiDung(
            String tuKhoa,
            String vaiTro,
            String trangThai,
            int trang,
            int kichThuoc) {

        // Kiểm tra chỉ số trang và số phần tử trên một trang
        if (trang < 0 || kichThuoc < 1 || kichThuoc > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0 và kích thước trang từ 1 đến 100"
            );
        }

        // Chưa tích hợp bảng người dùng chung
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chưa triển khai truy vấn danh sách người dùng"
        );
    }

    /**
     * Lấy thông tin chi tiết một người dùng.
     *
     * @param maNguoiDung Mã định danh tài khoản.
     * @return Thông tin tài khoản người dùng.
     */
    public AdminUserResponse layChiTietNguoiDung(
            Long maNguoiDung) {

        // Kiểm tra mã người dùng
        if (maNguoiDung == null || maNguoiDung <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Mã người dùng không hợp lệ"
            );
        }

        // Chưa có Repository truy vấn tài khoản
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Chưa triển khai truy vấn chi tiết người dùng"
        );
    }
}
