package com.blockchaincb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Phân quyền theo role; không chặn nhầm đường dẫn của Doctor và Patient. */
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain cauHinhBaoMat(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(phanQuyen -> phanQuyen
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/doctor/**", "/api/doctors/**").hasRole("DOCTOR")
                .requestMatchers("/api/patient/**", "/api/patients/**").hasRole("PATIENT")
                .anyRequest().denyAll())
            .formLogin(dangNhap -> dangNhap
                // Trả JSON sau đăng nhập để React xử lý chuyển trang theo vai trò.
                .successHandler((yeuCau, phanHoi, xacThuc) -> {
                    phanHoi.setStatus(200);
                    phanHoi.setContentType("application/json;charset=UTF-8");
                    String vaiTro = xacThuc.getAuthorities().iterator().next().getAuthority();
                    phanHoi.getWriter().write("{\"dangNhapThanhCong\":true,\"vaiTro\":\"" + vaiTro + "\"}");
                })
                .permitAll())
            .logout(dangXuat -> dangXuat
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll());
        // Bật CSRF mặc định vì sử dụng session/cookie.
        return http.build();
    }

    @Bean
    public PasswordEncoder boMaHoaMatKhau() {
        return new BCryptPasswordEncoder();
    }
}
