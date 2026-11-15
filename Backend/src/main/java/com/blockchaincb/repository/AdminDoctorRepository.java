
package com.blockchaincb.repository;

import java.util.List;

import com.blockchaincb.entity.Doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository quản lý bác sĩ dành cho Admin.
 *
 * Hỗ trợ:
 * - Lấy danh sách bác sĩ.
 * - Lọc bác sĩ chờ duyệt.
 * - Tìm kiếm động với Specification.
 * - Phân trang và sắp xếp.
 *
 * Không thay đổi dữ liệu Supabase.
 */
public interface AdminDoctorRepository
        extends JpaRepository<Doctor, Long>,
                JpaSpecificationExecutor<Doctor> {

    /**
     * Lấy bác sĩ theo trạng thái phê duyệt.
     */
    List<Doctor> findByTrangThaiPheDuyetIgnoreCase(
            String trangThaiPheDuyet
    );

    /**
     * Đếm bác sĩ theo trạng thái phê duyệt.
     */
    long countByTrangThaiPheDuyetIgnoreCase(
            String trangThaiPheDuyet
    );
}
