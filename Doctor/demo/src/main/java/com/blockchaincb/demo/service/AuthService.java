package com.blockchaincb.demo.service;

import com.blockchaincb.demo.dto.LoginRequest;
import com.blockchaincb.demo.dto.RegisterRequest;
import com.blockchaincb.demo.entity.Doctor;
import com.blockchaincb.demo.entity.User;
import com.blockchaincb.demo.repository.DoctorRepository;
import com.blockchaincb.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final DoctorRepository doctorRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final com.blockchaincb.demo.repository.SpecialtyRepository specialtyRepository;

  public AuthService(
      DoctorRepository doctorRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      com.blockchaincb.demo.repository.SpecialtyRepository specialtyRepository) {

    this.doctorRepository = doctorRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.specialtyRepository = specialtyRepository;
  }

  @Transactional
  public Doctor register(RegisterRequest request) {

    if (!specialtyRepository.existsById(request.getSpecialtyId())) {
      throw new com.blockchaincb.demo.exception.ApiException(400, "Chuyen khoa khong ton tai");
    }
    if (request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new com.blockchaincb.demo.exception.ApiException(
          400, "Mat khau vuot qua 72 byte UTF-8");
    }
    // 1. Kiem tra email trong bang users
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new com.blockchaincb.demo.exception.ApiException(409, "Email da ton tai");
    }

    // 2. Ma hoa mat khau
    String hashedPassword = passwordEncoder.encode(request.getPassword());

    // 3. Tao tai khoan User
    User user = new User();

    user.setFullName(request.getFullName());
    user.setEmail(request.getEmail());
    user.setPasswordHash(hashedPassword);
    user.setRole("DOCTOR");
    user.setEnabled(true);

    // 4. Luu User vao Supabase
    user = userRepository.save(user);

    // 5. Tao ho so Doctor
    Doctor doctor = new Doctor();

    doctor.setUser(user);
    doctor.setSpecialtyId(request.getSpecialtyId());
    doctor.setFullName(request.getFullName());
    doctor.setEmail(request.getEmail());
    doctor.setLicenseNumber(request.getLicenseNumber());
    doctor.setLicenseNo(request.getLicenseNumber());
    doctor.setHospital(request.getHospital());

    // Tuong thich tam thoi voi schema hien tai
    doctor.setPassword(hashedPassword);

    // 6. Trang thai cho Admin duyet
    doctor.setStatus("PENDING");
    doctor.setApprovalStatus("PENDING");

    // 7. Luu Doctor
    return doctorRepository.save(doctor);
  }

  @Transactional(readOnly = true)
  public Doctor login(LoginRequest request) {
    if (request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new com.blockchaincb.demo.exception.ApiException(401, "Email hoac mat khau khong dung");
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(
                () ->
                    new com.blockchaincb.demo.exception.ApiException(
                        401, "Email hoac mat khau khong dung"));
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new com.blockchaincb.demo.exception.ApiException(401, "Email hoac mat khau khong dung");
    }
    Doctor doctor =
        doctorRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new com.blockchaincb.demo.exception.ApiException(
                        403, "Tai khoan khong phai Doctor"));
    com.blockchaincb.demo.security.DoctorPolicy.requireAccount(doctor);
    return doctor;
  }
}
