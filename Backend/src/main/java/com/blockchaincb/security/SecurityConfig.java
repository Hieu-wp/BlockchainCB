
package com.blockchaincb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lớp cấu hình bảo mật cho hệ thống BlockchainCB.
 *
 * Chức năng:
 * - Cấu hình xác thực đăng nhập Admin.
 * - Phân quyền truy cập các API quản trị.
 * - Mã hóa mật khẩu bằng BCrypt.
 * - Quản lý đăng nhập và đăng xuất.
 * - Khởi tạo tài khoản Admin tạm thời trong bộ nhớ.
 */
@Configuration
public class SecurityConfig {

    /**
     * Cấu hình bộ lọc bảo mật của hệ thống.
     *
     * Chỉ tài khoản có vai trò ADMIN mới được truy cập
     * các API bắt đầu bằng /api/admin/.
     *
     * @param http Đối tượng cấu hình bảo mật HTTP.
     * @return Bộ lọc bảo mật đã được cấu hình.
     * @throws Exception Nếu xảy ra lỗi trong quá trình cấu hình.
     */
    @Bean
    public SecurityFilterChain cauHinhBaoMat(
            HttpSecurity http) throws Exception {

        http
            // Cấu hình quyền truy cập các đường dẫn
            .authorizeHttpRequests(phanQuyen -> phanQuyen

                // Cho phép truy cập trang đăng nhập và trang lỗi
                .requestMatchers(
                    "/login",
                    "/error"
                ).permitAll()

                // Chỉ Admin được truy cập API quản trị
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")

                // Từ chối những yêu cầu không được cấp quyền
                .anyRequest().denyAll()
            )

            // Cấu hình đăng nhập bằng tài khoản và mật khẩu
            .formLogin(dangNhap -> dangNhap

                // Chuyển đến trang thông tin Admin sau khi đăng nhập
                .defaultSuccessUrl("/api/admin/me", true)

                // Cho phép người chưa đăng nhập truy cập trang đăng nhập
                .permitAll()
            )

            // Cấu hình đăng xuất
            .logout(dangXuat -> dangXuat

                // Chuyển về trang đăng nhập sau khi đăng xuất
                .logoutSuccessUrl("/login?logout")

                // Hủy phiên đăng nhập hiện tại
                .invalidateHttpSession(true)

                // Xóa cookie phiên đăng nhập
                .deleteCookies("JSESSIONID")

                // Cho phép thực hiện chức năng đăng xuất
                .permitAll()
            );

        return http.build();
    }

    /**
     * Khởi tạo bộ mã hóa mật khẩu BCrypt.
     *
     * BCrypt được sử dụng để băm mật khẩu trước khi lưu trữ
     * và kiểm tra mật khẩu khi người dùng đăng nhập.
     *
     * @return Đối tượng mã hóa mật khẩu BCrypt.
     */
    @Bean
    public PasswordEncoder boMaHoaMatKhau() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Khởi tạo tài khoản Admin tạm thời trong bộ nhớ.
     *
     * Thông tin đăng nhập được lấy từ application.properties
     * thông qua các biến admin.username và admin.password.
     *
     * Mật khẩu được băm bằng BCrypt trước khi sử dụng.
     *
     * @param tenDangNhap Tên đăng nhập của Admin.
     * @param matKhau Mật khẩu của Admin.
     * @param boMaHoaMatKhau Bộ mã hóa mật khẩu.
     * @return Dịch vụ quản lý thông tin tài khoản.
     */
    @Bean
    public UserDetailsService dichVuTaiKhoan(
            @Value("${admin.username}") String tenDangNhap,
            @Value("${admin.password}") String matKhau,
            PasswordEncoder boMaHoaMatKhau) {

        // Tạo tài khoản Admin và mã hóa mật khẩu
        UserDetails taiKhoanAdmin = User.builder()
            .username(tenDangNhap)
            .password(boMaHoaMatKhau.encode(matKhau))
            .roles("ADMIN")
            .build();

        // Lưu tài khoản Admin tạm thời trong bộ nhớ
        return new InMemoryUserDetailsManager(taiKhoanAdmin);
    }
}
