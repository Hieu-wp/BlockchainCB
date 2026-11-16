package com.blockchaincb.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.blockchaincb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Truy vấn tài khoản trong public.users. */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from User n where n.maNguoiDung = :ma")
    Optional<User> layDeCapNhat(@Param("ma") Long maNguoiDung);
    Optional<User> findByEmailIgnoreCase(String email);
    long countByVaiTroIgnoreCase(String vaiTro);
    long countByDangHoatDong(boolean dangHoatDong);
}
