package com.blockchaincb.repository;

import java.util.List;
import com.blockchaincb.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn bác sĩ trong public.doctors. */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByTrangThaiPheDuyetIgnoreCase(String trangThaiPheDuyet);
    long countByTrangThaiPheDuyetIgnoreCase(String trangThaiPheDuyet);
}
