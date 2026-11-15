package com.blockchaincb.repository;

import java.util.Optional;
import com.blockchaincb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Truy vấn tài khoản trong public.users. */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);
    long countByVaiTroIgnoreCase(String vaiTro);
    long countByDangHoatDong(boolean dangHoatDong);
}
