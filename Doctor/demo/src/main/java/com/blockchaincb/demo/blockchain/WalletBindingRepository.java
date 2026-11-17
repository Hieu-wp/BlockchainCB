package com.blockchaincb.demo.blockchain;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WalletBindingRepository extends JpaRepository<WalletBinding, Long> {
  Optional<WalletBinding> findByAddress(String address);
}
