
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
 * Cấu hình bảo mật chung cho hệ thống BlockchainCB.
 *
 * Chức năng:
 * - Xác thực tài khoản Admin.
 * - Phân quyền truy cập Admin, Doctor và Patient.
 * - Mã hóa mật khẩu bằng BCrypt.
 * - Quản lý đăng nhập và đăng xuất.
 *
 * Lưu ý:
 * - Hiện tại chỉ có tài khoản Admin thử nghiệm.
 * - Doctor và Patient cần tích hợp xác thực chung sau.
 */
@Configuration
public class SecurityConfig {

    /**
     * Cấu hình xác thực và phân quyền HTTP.
     *
     * @param http Đối tượng cấu hình Spring Security.
     * @return Bộ lọc bảo mật đã được cấu hình.
     * @throws Exception Nếu cấu hình không thành công.
     */
    @Bean
    public SecurityFilterChain cauHinhBaoMat(
            HttpSecurity http) throws Exception {

        http
            // Cấu hình phân quyền theo từng nhóm API
            .authorizeHttpRequests(phanQuyen -> phanQuyen

                // Cho phép truy cập trang đăng nhập
                .requestMatchers(
                    "/login",
                    "/error"
                ).permitAll()

                // Chỉ ADMIN được truy cập API quản trị
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")

                // Chỉ DOCTOR được truy cập API bác sĩ
                .requestMatchers("/api/doctor/**")
                .hasRole("DOCTOR")

                // Chỉ PATIENT được truy cập API bệnh nhân
                .requestMatchers("/api/patient/**")
                .hasRole("PATIENT")

                // Từ chối các đường dẫn chưa được định nghĩa
                .anyRequest().denyAll()
            )

            // Cấu hình đăng nhập bằng tài khoản và mật khẩu
            .formLogin(dangNhap -> dangNhap

                // Chuyển Admin đến API thông tin đăng nhập
                .defaultSuccessUrl("/api/admin/me", true)

                // Cho phép truy cập trang đăng nhập
                .permitAll()
            )

            // Cấu hình đăng xuất
            .logout(dangXuat -> dangXuat

                // Chuyển về trang đăng nhập sau khi đăng xuất
                .logoutSuccessUrl("/login?logout")

                // Hủy phiên đăng nhập
                .invalidateHttpSession(true)

                // Xóa cookie phiên đăng nhập
                .deleteCookies("JSESSIONID")

                .permitAll()
            );

        return http.build();
    }

    /**
     * Khởi tạo bộ mã hóa mật khẩu BCrypt.
     *
     * @return Bộ mã hóa mật khẩu.
     */
    @Bean
    public PasswordEncoder boMaHoaMatKhau() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Khởi tạo tài khoản Admin tạm thời.
     *
     * Tên đăng nhập và mật khẩu được lấy từ
     * các biến môi trường của hệ thống.
     *
     * @param tenDangNhap Tên đăng nhập Admin.
     * @param matKhau Mật khẩu Admin.
     * @param boMaHoaMatKhau Bộ mã hóa mật khẩu.
     * @return Dịch vụ quản lý tài khoản trong bộ nhớ.
     */
    @Bean
    public UserDetailsService dichVuTaiKhoan(
            @Value("${admin.username}") String tenDangNhap,
            @Value("${admin.password}") String matKhau,
            PasswordEncoder boMaHoaMatKhau) {

        // Tạo tài khoản Admin thử nghiệm
        UserDetails taiKhoanAdmin = User.builder()
            .username(tenDangNhap)
            .password(boMaHoaMatKhau.encode(matKhau))
            .roles("ADMIN")
            .build();

        return new InMemoryUserDetailsManager(taiKhoanAdmin);
    }
}
