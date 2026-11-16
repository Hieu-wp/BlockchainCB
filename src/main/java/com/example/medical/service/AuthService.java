package com.example.medical.service;

import com.example.medical.dto.AuthResponse;
import com.example.medical.dto.LoginRequest;
import com.example.medical.dto.RegisterRequest;
import com.example.medical.entity.Enums.FollowStatus;
import com.example.medical.entity.Enums.Role;
import com.example.medical.entity.Patient;
import com.example.medical.entity.User;
import com.example.medical.repository.PatientRepository;
import com.example.medical.repository.UserRepository;
import com.example.medical.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Dang ky benh nhan: tao dong users + dong patients trong cung 1 transaction. */
    @Transactional
    public AuthResponse registerPatient(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setRole(Role.PATIENT);
        user = userRepository.save(user);

        Patient p = new Patient();
        p.setUser(user);
        // Ma BN duy nhat vi dua tren user id, vi du BN-2026-00012
        p.setPatientCode(String.format("BN-%d-%05d", Year.now().getValue(), user.getId()));
        p.setDateOfBirth(req.dateOfBirth());
        p.setGender(req.gender());
        p.setBloodType(req.bloodType());
        p.setPhone(req.phone());
        p.setAddress(req.address());
        p.setFollowStatus(FollowStatus.FOLLOWING);
        patientRepository.save(p);

        return new AuthResponse(jwtService.generateToken(user), user.getRole().name(), user.getFullName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        // Cung 1 thong bao loi cho ca "sai email" va "sai mat khau" de khong lo email nao ton tai
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));

        return new AuthResponse(jwtService.generateToken(user), user.getRole().name(), user.getFullName());
    }
}
