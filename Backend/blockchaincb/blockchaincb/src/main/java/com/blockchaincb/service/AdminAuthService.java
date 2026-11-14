
package com.blockchaincb.service;

import com.blockchaincb.entity.Admin;
import com.blockchaincb.repository.AdminRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

/**
 * Service xử lý nghiệp vụ xác thực tài khoản Admin.
 *
 * Chức năng:
 * - Tìm kiếm tài khoản Admin từ cơ sở dữ liệu.
 * - Kiểm tra trạng thái hoạt động của tài khoản.
 * - Cung cấp thông tin đăng nhập cho Spring Security.
 * - Cung cấp vai trò ADMIN để kiểm tra phân quyền.
 *
 * Lưu ý:
 * - Mật khẩu trong cơ sở dữ liệu phải được băm bằng BCrypt.
 * - Không trả mật khẩu hoặc mã băm mật khẩu qua REST API.
 * - Không cho phép người dùng tự đăng ký tài khoản Admin.
 */
@Service
public class AdminAuthService implements UserDetailsService {

    // Repository truy vấn tài khoản Admin trong MySQL
    private final AdminRepository adminRepository;

    /**
     * Khởi tạo Service bằng cơ chế Dependency Injection.
     *
     * @param adminRepository Repository quản lý tài khoản Admin.
     */
    public AdminAuthService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /**
     * Tải tài khoản Admin theo tên đăng nhập.
     *
     * Spring Security tự động gọi phương thức này
     * trong quá trình xác thực username/password.
     *
     * @param tenDangNhap Tên đăng nhập của Admin.
     * @return Thông tin tài khoản theo chuẩn UserDetails.
     * @throws UsernameNotFoundException Nếu tài khoản không tồn tại.
     */
    @Override
    public UserDetails loadUserByUsername(String tenDangNhap)
            throws UsernameNotFoundException {

        // Tìm tài khoản Admin trong cơ sở dữ liệu
        Admin taiKhoanAdmin = adminRepository
                .findByTenDangNhap(tenDangNhap)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Tên đăng nhập hoặc mật khẩu không hợp lệ"
                    )
                );

        // Trả về thông tin xác thực cho Spring Security
        return User.builder()
                .username(taiKhoanAdmin.getTenDangNhap())
                .password(taiKhoanAdmin.getMatKhau())
                .roles("ADMIN")
                .disabled(!taiKhoanAdmin.isDangHoatDong())
                .build();
    }
}
