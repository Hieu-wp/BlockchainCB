package com.blockchaincb.demo.service;

import com.blockchaincb.demo.entity.AuditLog;
import com.blockchaincb.demo.repository.AuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
  private final AuditLogRepository logs;

  public AuditService(AuditLogRepository logs) {
    this.logs = logs;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Long actor, String action, String resource, String outcome) {
    var log = new AuditLog();
    log.setActorUserId(actor);
    log.setAction(action);
    log.setResource(resource);
    log.setOutcome(outcome);
    log.setOccurredAt(Instant.now());
    logs.saveAndFlush(log);
  }
}
