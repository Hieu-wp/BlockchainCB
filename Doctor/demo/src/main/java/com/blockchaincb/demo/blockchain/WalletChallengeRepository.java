package com.blockchaincb.demo.blockchain;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletChallengeRepository extends JpaRepository<WalletChallenge, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from WalletChallenge c where c.id=:id and c.userId=:user")
  Optional<WalletChallenge> owned(@Param("id") String id, @Param("user") Long user);
}
