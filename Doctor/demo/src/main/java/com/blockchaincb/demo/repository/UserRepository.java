package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
  Optional<User> lockAccount(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Query(
      "select count(u)>0 from User u where lower(u.walletAddress)=:wallet and u.id<>:owner")
  boolean walletInUse(
      @org.springframework.data.repository.query.Param("wallet") String wallet,
      @org.springframework.data.repository.query.Param("owner") Long owner);
}
