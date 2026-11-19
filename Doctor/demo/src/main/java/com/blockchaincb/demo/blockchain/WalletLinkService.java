package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.UserRepository;
import com.blockchaincb.demo.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

@Service
@Transactional
public class WalletLinkService {
  private final WalletBindingRepository bindings;
  private final WalletChallengeRepository challenges;
  private final UserRepository users;
  private final DoctorIdentityService identities;
  private final WalletOriginPolicy origins;

  public WalletLinkService(
      WalletBindingRepository bindings,
      WalletChallengeRepository challenges,
      UserRepository users,
      DoctorIdentityService identities,
      WalletOriginPolicy origins) {
    this.bindings = bindings;
    this.challenges = challenges;
    this.users = users;
    this.identities = identities;
    this.origins = origins;
  }

  public record Challenge(String challengeId, String message, Instant expiresAt, String origin) {}

  public record Linked(String walletAddress, long chainId, Instant verifiedAt) {}

  public record WalletStatus(
      String walletAddress, boolean verified, Long chainId, Instant verifiedAt) {}

  @Transactional(readOnly = true)
  public WalletStatus status(DoctorPrincipal actor) {
    var d = identities.account(actor);
    String saved = d.getUser().getWalletAddress();
    var proof = bindings.findById(actor.userId());
    boolean verified =
        saved != null
            && proof.isPresent()
            && proof.get().address.equalsIgnoreCase(saved)
            && proof.get().chainId == ChainSettings.CHAIN
            && proof.get().verifiedAt != null
            && "PERSONAL_SIGN".equals(proof.get().proofMethod);
    return new WalletStatus(
        saved,
        verified,
        verified ? proof.get().chainId : null,
        verified ? proof.get().verifiedAt : null);
  }

  public Challenge challenge(DoctorPrincipal actor, String address) {
    return challenge(actor, address, origins.primaryOrigin());
  }

  public Challenge challenge(DoctorPrincipal actor, String address, String requestedOrigin) {
    String origin = origins.requireAllowed(requestedOrigin);
    var d = identities.walletLinkAllowed(actor);
    var user =
        users
            .lockAccount(actor.userId())
            .orElseThrow(() -> new ApiException(403, "ACCOUNT_MISSING"));
    String wallet = ChainSettings.address(address);
    if (status(actor).verified() && wallet.equalsIgnoreCase(user.getWalletAddress()))
      throw new ApiException(409, "WALLET_ALREADY_VERIFIED");
    bindings
        .findById(user.getId())
        .ifPresent(
            b -> {
              if (!b.address.equals(wallet))
                throw new ApiException(409, "WALLET_CHANGE_REQUIRES_REVIEW");
            });
    if (users.walletInUse(wallet, actor.userId()))
      throw new ApiException(409, "WALLET_ALREADY_LINKED");
    byte[] random = new byte[32];
    new java.security.SecureRandom().nextBytes(random);
    var c = new WalletChallenge();
    c.id = UUID.randomUUID().toString();
    c.userId = actor.userId();
    c.doctorId = d.getId();
    c.address = wallet;
    c.expiresAt = Instant.now().plusSeconds(300);
    c.message =
        "MedLedger wallet ownership verification\nDomain: "
            + java.net.URI.create(origin).getRawAuthority()
            + "\nURI: "
            + origin
            + "\nChain ID: "
            + ChainSettings.CHAIN
            + "\nUser: "
            + actor.userId()
            + "\nDoctor: "
            + actor.doctorId()
            + "\nPurpose: LINK_DOCTOR_WALLET_ONLY\nAddress: "
            + wallet
            + "\nNonce: "
            + HexFormat.of().formatHex(random)
            + "\nChallenge: "
            + c.id
            + "\nExpires: "
            + c.expiresAt;
    challenges.saveAndFlush(c);
    return new Challenge(c.id, c.message, c.expiresAt, origin);
  }

  public Linked verify(DoctorPrincipal actor, String id, String signature) {
    identities.walletLinkAllowed(actor);
    var user =
        users
            .lockAccount(actor.userId())
            .orElseThrow(() -> new ApiException(403, "ACCOUNT_MISSING"));
    var c =
        challenges
            .owned(id, actor.userId())
            .orElseThrow(() -> new ApiException(400, "CHALLENGE_NOT_OWNED"));
    if (c.consumedAt != null
        || !c.expiresAt.isAfter(Instant.now())
        || !actor.doctorId().equals(c.doctorId))
      throw new ApiException(409, "CHALLENGE_USED_OR_EXPIRED");
    if (!recover(c.message, signature).equals(c.address))
      throw new ApiException(400, "INVALID_SIGNATURE");
    if (users.walletInUse(c.address, actor.userId()))
      throw new ApiException(409, "WALLET_ALREADY_LINKED");
    var previous = bindings.findById(actor.userId());
    if (previous.isPresent() && !previous.get().address.equals(c.address))
      throw new ApiException(409, "WALLET_CHANGE_REQUIRES_REVIEW");
    var b = previous.orElseGet(WalletBinding::new);
    b.userId = actor.userId();
    b.address = c.address;
    b.chainId = ChainSettings.CHAIN;
    b.verifiedAt = Instant.now();
    b.proofMethod = "PERSONAL_SIGN";
    bindings.saveAndFlush(b);
    c.consumedAt = Instant.now();
    user.setWalletAddress(c.address);
    return new Linked(b.address, b.chainId, b.verifiedAt);
  }

  public static String recover(String message, String signature) {
    try {
      if (signature == null || !signature.matches("0x[0-9a-fA-F]{130}"))
        throw new IllegalArgumentException();
      byte[] s = Numeric.hexStringToByteArray(signature);
      byte v = s[64];
      if (v < 27) v += 27;
      if (v != 27 && v != 28) throw new IllegalArgumentException();
      var data =
          new Sign.SignatureData(v, Arrays.copyOfRange(s, 0, 32), Arrays.copyOfRange(s, 32, 64));
      return "0x"
          + Keys.getAddress(
              Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), data));
    } catch (Exception e) {
      throw new ApiException(400, "INVALID_SIGNATURE");
    }
  }

  public String requiredWallet(Long userId, String rawAddress) {
    String wallet = ChainSettings.address(rawAddress);
    var b =
        bindings.findById(userId).orElseThrow(() -> new ApiException(409, "WALLET_PROOF_MISSING"));
    if (b.chainId != ChainSettings.CHAIN
        || !"PERSONAL_SIGN".equals(b.proofMethod)
        || b.verifiedAt == null
        || !b.address.equals(wallet)) throw new ApiException(409, "WALLET_PROOF_MISMATCH");
    return wallet;
  }
}
