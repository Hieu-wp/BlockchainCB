package com.blockchaincb.demo.blockchain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.UserRepository;
import com.blockchaincb.demo.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

class WalletLinkServiceTest {
  WalletBindingRepository bindings = mock(WalletBindingRepository.class);
  WalletChallengeRepository challenges = mock(WalletChallengeRepository.class);
  UserRepository users = mock(UserRepository.class);
  DoctorIdentityService identity = mock(DoctorIdentityService.class);
  DoctorPrincipal actor = new DoctorPrincipal(1L, 10L);
  WalletLinkService service;
  WalletChallenge challenge;
  ECKeyPair key;
  String wallet;
  User user;

  @BeforeEach
  void setup() throws Exception {
    key = Keys.createEcKeyPair();
    wallet = "0x" + Keys.getAddress(key);
    user = new User();
    user.setId(1L);
    var d = mock(Doctor.class);
    when(d.getId()).thenReturn(10L);
    when(identity.walletLinkAllowed(actor)).thenReturn(d);
    when(d.getUser()).thenReturn(user);
    when(identity.account(actor)).thenReturn(d);
    when(users.lockAccount(1L)).thenReturn(Optional.of(user));
    when(challenges.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              challenge = i.getArgument(0);
              return challenge;
            });
    when(challenges.owned(anyString(), eq(1L)))
        .thenAnswer(
            i ->
                challenge != null && challenge.id.equals(i.getArgument(0))
                    ? Optional.of(challenge)
                    : Optional.empty());
    service =
        new WalletLinkService(
            bindings,
            challenges,
            users,
            identity,
            new ChainSettings(false, false, "", "localhost:5173", 3));
  }

  String signature(ECKeyPair signer) {
    var s = Sign.signPrefixedMessage(challenge.message.getBytes(StandardCharsets.UTF_8), signer);
    byte[] out = new byte[65];
    System.arraycopy(s.getR(), 0, out, 0, 32);
    System.arraycopy(s.getS(), 0, out, 32, 32);
    out[64] = s.getV()[0];
    return Numeric.toHexString(out);
  }

  @Test
  void challengeBindsDomainChainIdentityPurposeAndExpires() {
    var c = service.challenge(actor, wallet);
    assertTrue(c.message().contains("Domain: localhost:5173"));
    assertTrue(c.message().contains("Chain ID: 11155111"));
    assertTrue(c.message().contains("Doctor: 10"));
    assertTrue(c.message().contains("LINK_DOCTOR_WALLET_ONLY"));
    assertTrue(c.expiresAt().isAfter(Instant.now()));
  }

  @Test
  void validPersonalSignatureLinksAndCannotReplay() {
    service.challenge(actor, wallet);
    String sig = signature(key);
    assertEquals(wallet, service.verify(actor, challenge.id, sig).walletAddress());
    assertEquals(wallet, user.getWalletAddress());
    assertNotNull(challenge.consumedAt);
    assertEquals(
        409,
        assertThrows(ApiException.class, () -> service.verify(actor, challenge.id, sig))
            .getStatus());
  }

  @Test
  void wrongSignerRejected() throws Exception {
    service.challenge(actor, wallet);
    assertEquals(
        400,
        assertThrows(
                ApiException.class,
                () -> service.verify(actor, challenge.id, signature(Keys.createEcKeyPair())))
            .getStatus());
    assertNull(user.getWalletAddress());
  }

  @Test
  void alteredMessageRejected() {
    service.challenge(actor, wallet);
    String sig = signature(key);
    challenge.message += "tamper";
    assertEquals(
        400,
        assertThrows(ApiException.class, () -> service.verify(actor, challenge.id, sig))
            .getStatus());
  }

  @Test
  void expiryAndDifferentOwnerRejected() {
    service.challenge(actor, wallet);
    challenge.expiresAt = Instant.now().minusSeconds(1);
    assertEquals(
        409,
        assertThrows(ApiException.class, () -> service.verify(actor, challenge.id, signature(key)))
            .getStatus());
    assertEquals(
        400,
        assertThrows(
                ApiException.class,
                () -> service.verify(actor, "another-challenge", signature(key)))
            .getStatus());
  }

  @Test
  void walletAlreadyUsedRejected() {
    when(users.walletInUse(wallet, 1L)).thenReturn(true);
    assertEquals(
        409, assertThrows(ApiException.class, () -> service.challenge(actor, wallet)).getStatus());
  }

  @Test
  void legacyAddressIsNotProof() {
    assertEquals(
        409,
        assertThrows(ApiException.class, () -> service.requiredWallet(1L, wallet)).getStatus());
  }

  @Test
  void malformedSignatureIsRejected() {
    assertEquals(
        400,
        assertThrows(ApiException.class, () -> WalletLinkService.recover("message", "bad"))
            .getStatus());
  }

  @Test
  void walletStatusDoesNotTreatLegacyAddressAsVerified() {
    user.setWalletAddress(wallet);
    assertEquals(wallet, service.status(actor).walletAddress());
    assertFalse(service.status(actor).verified());
    assertNull(service.status(actor).verifiedAt());
  }

  @Test
  void walletStatusRequiresMatchingProofAndCurrentChain() {
    user.setWalletAddress(wallet);
    var b = new WalletBinding();
    b.userId = 1L;
    b.address = wallet;
    b.chainId = ChainSettings.CHAIN;
    b.verifiedAt = Instant.now();
    b.proofMethod = "PERSONAL_SIGN";
    when(bindings.findById(1L)).thenReturn(Optional.of(b));
    assertTrue(service.status(actor).verified());
    b.chainId = 1L;
    assertFalse(service.status(actor).verified());
    b.chainId = ChainSettings.CHAIN;
    b.address = "0x" + "2".repeat(40);
    assertFalse(service.status(actor).verified());
  }

  @Test
  void alreadyVerifiedSameWalletDoesNotNeedAnotherChallenge() {
    user.setWalletAddress(wallet);
    var b = new WalletBinding();
    b.userId = 1L;
    b.address = wallet;
    b.chainId = ChainSettings.CHAIN;
    b.verifiedAt = Instant.now();
    b.proofMethod = "PERSONAL_SIGN";
    when(bindings.findById(1L)).thenReturn(Optional.of(b));
    assertEquals(
        "WALLET_ALREADY_VERIFIED",
        assertThrows(ApiException.class, () -> service.challenge(actor, wallet)).getCode());
    verify(challenges, never()).saveAndFlush(any());
  }
}
