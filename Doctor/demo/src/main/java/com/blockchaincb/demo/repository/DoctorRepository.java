package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.Doctor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

  Optional<Doctor> findByEmail(String email);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
  Optional<Doctor> findByUserId(Long userId);

  boolean existsByEmail(String email);
}
