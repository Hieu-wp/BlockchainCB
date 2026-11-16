package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.AuditLog;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
