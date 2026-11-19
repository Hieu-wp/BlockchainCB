package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.security.DoctorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/doctor")
public class BlockchainController {
  private final WalletLinkService wallets;
  private final AnchorService anchors;
  private final ChainSettings settings;
  private final WalletOriginPolicy origins;

  public BlockchainController(
      WalletLinkService wallets, AnchorService anchors, ChainSettings settings, WalletOriginPolicy origins) {
    this.wallets = wallets;
    this.anchors = anchors;
    this.settings = settings;
    this.origins = origins;
  }

  public record WalletRequest(@NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String address,
      @NotBlank @Size(max = 255) String origin) {}

  public record SignatureRequest(
      @NotBlank @Size(max = 64) String challengeId,
      @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{130}") String signature) {
    @Override
    public String toString() {
      return "SignatureRequest[REDACTED]";
    }
  }

  public record TransactionRequest(
      @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{64}") String transactionHash) {}

  public record Config(
      boolean enabled,
      long chainId,
      String contractAddress,
      String domain,
      String walletLinkPolicy, java.util.List<String> walletOrigins) {}

  @GetMapping("/blockchain/config")
  public Config config() {
    return new Config(
        settings.enabled && settings.reviewed && !settings.rpc.isBlank(),
        ChainSettings.CHAIN,
        ChainSettings.CONTRACT,
        origins.primaryDomain(),
        "PENDING_APPROVED_V2_ORIGIN", origins.allowedOrigins());
  }

  @GetMapping("/wallet")
  public WalletLinkService.WalletStatus wallet(@AuthenticationPrincipal DoctorPrincipal p) {
    return wallets.status(p);
  }

  @PostMapping("/wallet/challenges")
  public WalletLinkService.Challenge challenge(
      @AuthenticationPrincipal DoctorPrincipal p, @Valid @RequestBody WalletRequest r,
      @RequestHeader(value = "Origin", required = false) String browserOrigin) {
    if (browserOrigin != null && !browserOrigin.equals(r.origin()))
      throw new com.blockchaincb.demo.exception.ApiException(403, "WALLET_ORIGIN_NOT_ALLOWED");
    return wallets.challenge(p, r.address(), r.origin());
  }

  @PostMapping("/wallet/verify")
  public WalletLinkService.Linked verify(
      @AuthenticationPrincipal DoctorPrincipal p, @Valid @RequestBody SignatureRequest r) {
    return wallets.verify(p, r.challengeId(), r.signature());
  }

  // POST: preparation reserves and persists an intent before MetaMask broadcasts.
  @PostMapping("/records/{id}/versions/{number}/anchor-payload")
  public AnchorService.Prepared payload(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number) {
    return anchors.prepare(p, id, number);
  }

  @PostMapping("/records/{id}/versions/{number}/anchor-transactions")
  public AnchorService.Status submit(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number,
      @Valid @RequestBody TransactionRequest r) {
    return anchors.submit(p, id, number, r.transactionHash());
  }

  @GetMapping("/records/{id}/versions/{number}/anchor-status")
  public AnchorService.Status status(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number) {
    return anchors.status(p, id, number);
  }
}
