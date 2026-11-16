
package com.blockchaincb.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Doctor b where b.maBacSi = :ma")
    Optional<Doctor> layDeCapNhat(@Param("ma") Long maBacSi);

    // Chỉ cập nhật cột phê duyệt, không ghi lại mật khẩu hoặc hồ sơ gốc.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Doctor b set b.trangThaiPheDuyet = 'REJECTED' "
            + "where b.maBacSi = :ma and upper(b.trangThaiPheDuyet) = 'PENDING'")
    int tuChoiHoSoChoDuyet(@Param("ma") Long maBacSi);

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
