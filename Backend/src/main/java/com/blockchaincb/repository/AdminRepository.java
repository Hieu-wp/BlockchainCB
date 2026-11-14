
package com.blockchaincb.repository;

import com.blockchaincb.entity.Admin;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository quản lý dữ liệu tài khoản Admin.
 *
 * Chức năng:
 * - Truy vấn tài khoản Admin theo tên đăng nhập.
 * - Kiểm tra tên đăng nhập đã tồn tại hay chưa.
 * - Kiểm tra email đã được sử dụng hay chưa.
 * - Tìm tài khoản Admin theo địa chỉ ví MetaMask.
 * - Lấy danh sách tài khoản Admin đang hoạt động.
 * - Hỗ trợ lưu, cập nhật và xóa dữ liệu Admin.
 *
 * Repository sử dụng Spring Data JPA để làm việc
 * với bảng admins trong cơ sở dữ liệu.
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    /**
     * Tìm tài khoản Admin theo tên đăng nhập.
     *
     * Phục vụ chức năng xác thực khi Admin đăng nhập.
     *
     * @param tenDangNhap Tên đăng nhập cần tìm.
     * @return Tài khoản Admin nếu tồn tại.
     */
    Optional<Admin> findByTenDangNhap(String tenDangNhap);

    /**
     * Kiểm tra tên đăng nhập đã tồn tại.
     *
     * @param tenDangNhap Tên đăng nhập cần kiểm tra.
     * @return true nếu đã tồn tại, ngược lại false.
     */
    boolean existsByTenDangNhap(String tenDangNhap);

    /**
     * Kiểm tra email đã được sử dụng.
     *
     * @param email Email cần kiểm tra.
     * @return true nếu email đã tồn tại.
     */
    boolean existsByEmail(String email);

    /**
     * Tìm tài khoản Admin theo địa chỉ ví MetaMask.
     *
     * @param diaChiVi Địa chỉ ví blockchain.
     * @return Tài khoản Admin nếu tìm thấy.
     */
    Optional<Admin> findByDiaChiVi(String diaChiVi);

    /**
     * Lấy danh sách tài khoản Admin đang hoạt động.
     *
     * @param dangHoatDong Trạng thái hoạt động.
     * @return Danh sách tài khoản phù hợp.
     */
    List<Admin> findByDangHoatDong(boolean dangHoatDong);

    /**
     * Đếm số lượng tài khoản Admin theo trạng thái.
     *
     * @param dangHoatDong Trạng thái cần thống kê.
     * @return Số lượng tài khoản Admin.
     */
    long countByDangHoatDong(boolean dangHoatDong);
}
