package com.blockchaincb.demo.security;

import com.blockchaincb.demo.exception.ApiException;
import io.jsonwebtoken.*;
import jakarta.validation.Validator;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Doctor-only, single-instance JWE envelope. No password, nonce or private key is persisted/logged. */
@Service
public class AuthPayloadService {
  private final KeyPair keys;
  private final String keyId = UUID.randomUUID().toString();
  private final Clock clock;
  private final Validator validator;
  private final JwtParser parser;
  private final JsonMapper json = JsonMapper.builder().build();
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Pending> pending = new HashMap<>();

  private record Pending(String purpose, Instant expiresAt) {}

  public record Challenge(
      String keyId, Map<String, Object> publicKey, String nonce, String purpose, Instant expiresAt) {}

  @Autowired
  public AuthPayloadService(Validator validator) {
    this(validator, Clock.systemUTC(), generateKeys());
  }

  AuthPayloadService(Validator validator, Clock clock, KeyPair keys) {
    this.validator = validator;
    this.clock = clock;
    this.keys = keys;
    this.parser = Jwts.parser().keyLocator(header -> {
          // Check before decryption/decompression; never follow keys/URLs supplied by the client.
          if (!(header instanceof JweHeader h) || header.containsKey("zip")
              || !"RSA-OAEP-256".equals(h.getAlgorithm()) || !"A256GCM".equals(h.getEncryptionAlgorithm())
              || !keyId.equals(h.getKeyId()) || !"doctor-auth+jwe".equals(h.getType())) {
            throw new MalformedJwtException("Invalid Doctor auth envelope header");
          }
          return keys.getPrivate();
        })
        .clock(() -> Date.from(clock.instant()))
        .requireIssuer("blockchaincb-doctor-ui").requireAudience("doctor-auth").build();
  }

  private static KeyPair generateKeys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException("Doctor auth encryption unavailable");
    }
  }

  private static String unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public synchronized Challenge challenge(String purpose) {
    if (!Set.of("login", "register").contains(purpose)) throw invalid();
    var now = clock.instant();
    pending.values().removeIf(p -> !p.expiresAt().isAfter(now));
    if (pending.size() >= 1024) throw new ApiException(429, "AUTH_CHALLENGE_BUSY");
    byte[] nonceBytes = new byte[32];
    random.nextBytes(nonceBytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    Instant expiry = now.truncatedTo(ChronoUnit.SECONDS).plusSeconds(120);
    pending.put(nonce, new Pending(purpose, expiry));
    var key = (RSAPublicKey) keys.getPublic();
    return new Challenge(keyId, Map.of("kty", "RSA", "n", unsigned(key.getModulus()),
        "e", unsigned(key.getPublicExponent()), "alg", "RSA-OAEP-256", "key_ops", List.of("encrypt"),
        "ext", true), nonce, purpose, expiry);
  }

  private ApiException invalid() {
    // Do not echo ciphertext, decoded claims, validation values or crypto exceptions.
    return new ApiException(400, "AUTH_ENVELOPE_INVALID", "Yeu cau dang nhap ma hoa khong hop le hoac het han");
  }

  public <T> T open(String envelope, String purpose, Class<T> type) {
    try {
      if (envelope == null || envelope.length() > 16384) throw invalid();
      var jwe = parser.parseEncryptedClaims(envelope);
      var header = jwe.getHeader();
      if (!keyId.equals(header.getKeyId()) || !"doctor-auth+jwe".equals(header.getType())) throw invalid();
      var claims = jwe.getPayload();
      if (!purpose.equals(claims.get("purpose")) || claims.getId() == null || claims.getExpiration() == null) throw invalid();
      Pending challenge;
      synchronized (this) {
        challenge = pending.get(claims.getId());
        if (challenge == null || !purpose.equals(challenge.purpose())
            || !challenge.expiresAt().isAfter(clock.instant())
            || !challenge.expiresAt().equals(claims.getExpiration().toInstant())) throw invalid();
        // Atomic one-use consumption occurs before credentials are checked, including failed login.
        pending.remove(claims.getId());
      }
      if (!(claims.get("credentials") instanceof Map<?, ?> credentials)) throw invalid();
      T request = json.convertValue(credentials, type);
      if (!validator.validate(request).isEmpty()) throw invalid();
      return request;
    } catch (JwtException | IllegalArgumentException | tools.jackson.core.JacksonException malformed) {
      throw invalid();
    }
  }
}
