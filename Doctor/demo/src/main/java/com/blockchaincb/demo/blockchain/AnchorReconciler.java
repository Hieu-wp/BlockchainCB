package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.security.DoctorPrincipal;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class AnchorReconciler {
  private final AnchorAttemptRepository attempts;
  private final AnchorService service;
  private final ChainSettings settings;
  private final com.blockchaincb.demo.service.AuditService audit;

  public AnchorReconciler(
      AnchorAttemptRepository attempts,
      AnchorService service,
      ChainSettings settings,
      com.blockchaincb.demo.service.AuditService audit) {
    this.attempts = attempts;
    this.service = service;
    this.settings = settings;
    this.audit = audit;
  }

  @Scheduled(
      fixedDelayString = "${doctor.blockchain.reconcile-delay-ms:60000}",
      initialDelayString = "${doctor.blockchain.reconcile-delay-ms:60000}")
  public void check() {
    if (!settings.enabled || !settings.reviewed || settings.rpc.isBlank()) return;
    for (var a : attempts.due(Instant.now().minusSeconds(30), PageRequest.of(0, 10))) {
      try {
        String resource =
            "/api/doctor/records/" + a.recordId + "/versions/" + a.versionNumber + "/anchor-status";
        audit.record(a.userId, "ANCHOR_RECONCILE", resource, "ATTEMPT");
        var result =
            service.status(new DoctorPrincipal(a.userId, a.doctorId), a.recordId, a.versionNumber);
        audit.record(a.userId, "ANCHOR_RECONCILE", resource, result.status());
      } catch (RuntimeException ignored) {
        /* No sensitive exception/RPC URL logs. Next bounded batch can retry. */
      }
    }
  }
}
