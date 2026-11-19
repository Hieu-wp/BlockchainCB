package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicit opt-in. No new JPA mappings or automatic changes to the team's public schema. */
@Component
public class SharedIntegrationSettings {
  public final boolean enabled;

  public SharedIntegrationSettings(
      @Value("${doctor.integration.shared-grants-enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  public void requireEnabled() {
    if (!enabled) throw new ApiException(503, "SHARED_GRANTS_DISABLED");
  }

  public void requireLegacyRecords() {
    // Public patient IDs must NEVER be used as doctor_backend.patient_links IDs.
    if (enabled) throw new ApiException(503, "SHARED_RECORDS_NOT_READY");
  }
}
