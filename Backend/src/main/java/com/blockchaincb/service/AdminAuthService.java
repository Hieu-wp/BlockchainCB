package com.blockchaincb.service;

import java.util.Locale;
import com.blockchaincb.entity.User;
import com.blockchaincb.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Nguồn xác thực chung từ public.users.
 * Yêu cầu password_hash là BCrypt; nếu nhóm dùng Supabase Auth/JWT thì thay cơ chế này.
 */
@Service
public class AdminAuthService implements UserDetailsService {
    private final UserRepository khoNguoiDung;

    public AdminAuthService(UserRepository khoNguoiDung) {
        this.khoNguoiDung = khoNguoiDung;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User nguoiDung = khoNguoiDung.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không hợp lệ"));

        // Không tự nâng quyền: chỉ chấp nhận các vai trò đã khai báo trong hệ thống.
        String vaiTro = nguoiDung.getVaiTro().trim().toUpperCase(Locale.ROOT);
        if (!vaiTro.equals("ADMIN") && !vaiTro.equals("DOCTOR") && !vaiTro.equals("PATIENT")) {
            throw new UsernameNotFoundException("Vai trò tài khoản không hợp lệ");
        }

        return org.springframework.security.core.userdetails.User.builder()
            .username(nguoiDung.getEmail())
            .password(nguoiDung.getMatKhauMaHoa())
            .roles(vaiTro)
            .disabled(!nguoiDung.isDangHoatDong())
            .build();
    }
}
