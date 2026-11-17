package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChainSettings {
  public static final long CHAIN = 11155111L;
  public static final String CONTRACT = "0xcd49fa3ee662e0bcfada0fea9dd69f9383d6f7735";
  public static final String ZERO = "0x" + "0".repeat(64);
  public final boolean enabled, reviewed;
  public final String rpc, domain;
  public final int confirmations;

  public ChainSettings(
      @Value("${doctor.blockchain.enabled:false}") boolean enabled,
      @Value("${doctor.blockchain.deployment-reviewed:false}") boolean reviewed,
      @Value("${doctor.blockchain.rpc-url:}") String rpc,
      @Value("${doctor.wallet.domain:localhost:5173}") String domain,
      @Value("${doctor.blockchain.confirmations:3}") int confirmations) {
    this.enabled = enabled;
    this.reviewed = reviewed;
    this.rpc = rpc;
    this.domain = domain;
    if (confirmations < 1) throw new IllegalArgumentException("Confirmations must be positive");
    if (domain.contains("\n") || domain.contains("\r"))
      throw new IllegalArgumentException("Invalid wallet domain");
    this.confirmations = confirmations;
  }

  public void requireEnabled() {
    if (!enabled || !reviewed || rpc.isBlank())
      throw new ApiException(
          503,
          "BLOCKCHAIN_DISABLED: deployment/admin security review and RPC configuration required");
  }

  public static String address(String value) {
    if (value == null
        || !value.matches("0x[0-9a-fA-F]{40}")
        || value.equalsIgnoreCase("0x" + "0".repeat(40)))
      throw new ApiException(400, "INVALID_WALLET");
    return value.toLowerCase(java.util.Locale.ROOT);
  }

  public static String hex32(String value) {
    if (value == null || !value.matches("0x[0-9a-fA-F]{64}"))
      throw new ApiException(400, "INVALID_HEX32");
    return value.toLowerCase(java.util.Locale.ROOT);
  }
}
