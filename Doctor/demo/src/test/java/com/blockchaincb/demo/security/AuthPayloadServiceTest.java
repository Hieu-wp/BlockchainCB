package com.blockchaincb.demo.security;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.exception.ApiException;
import io.jsonwebtoken.Jwts;
import jakarta.validation.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

public class AuthPayloadServiceTest {
  static KeyPair pair;
  static ValidatorFactory factory;
  AuthPayloadService service;
  static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");
  private static final Map<String,Object> LOGIN = Map.of("email","fixture@example.test","password","Synthetic-only!123");

  static class TestClock extends Clock {
    Instant now = NOW;
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
  TestClock clock;

  @BeforeAll static void init() throws Exception {
    var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);pair=generator.generateKeyPair();
    factory=Validation.buildDefaultValidatorFactory();
  }
  @AfterAll static void close() { factory.close(); }
  @BeforeEach void setup() { clock=new TestClock();service=new AuthPayloadService(factory.getValidator(),clock,pair); }

  public static PublicKey publicKey(AuthPayloadService.Challenge challenge) throws Exception {
    var jwk=challenge.publicKey();
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
        new BigInteger(1,Base64.getUrlDecoder().decode((String)jwk.get("n"))),
        new BigInteger(1,Base64.getUrlDecoder().decode((String)jwk.get("e")))));
  }
  public static String encrypt(AuthPayloadService.Challenge c, Map<String,Object> credentials) throws Exception {
    return Jwts.builder().header().keyId(c.keyId()).type("doctor-auth+jwe").and()
        .issuer("blockchaincb-doctor-ui").audience().add("doctor-auth").and()
        .id(c.nonce()).claim("purpose",c.purpose()).expiration(Date.from(c.expiresAt()))
        .claim("credentials",credentials).encryptWith(publicKey(c),Jwts.KEY.RSA_OAEP_256,Jwts.ENC.A256GCM).compact();
  }

  @Test void opensOnceWithoutExposingPrivateKeyOrPasswordInPublicChallenge() throws Exception {
    var c=service.challenge("login");var payload=encrypt(c,LOGIN);
    assertFalse(payload.contains((String)LOGIN.get("password")));
    assertFalse(c.publicKey().containsKey("d"));
    assertEquals(LOGIN.get("password"),service.open(payload,"login",LoginRequest.class).getPassword());
    assertEquals("AUTH_ENVELOPE_INVALID",assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class)).getCode());
  }
  @Test void rejectsExpiredChallenge() throws Exception {
    var payload=encrypt(service.challenge("login"),LOGIN);clock.now=NOW.plusSeconds(121);
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
  }
  @Test void rejectsWrongPurposeWithoutConsumingValidChallenge() throws Exception {
    var payload=encrypt(service.challenge("login"),LOGIN);
    assertThrows(ApiException.class,()->service.open(payload,"register",RegisterRequest.class));
    assertNotNull(service.open(payload,"login",LoginRequest.class));
  }
  @Test void rejectsTamperAndDoesNotEchoSecrets() throws Exception {
    var payload=encrypt(service.challenge("login"),LOGIN);var parts=payload.split("\\.");
    byte[] cipher=Base64.getUrlDecoder().decode(parts[3]);cipher[0]^=1;
    parts[3]=Base64.getUrlEncoder().withoutPadding().encodeToString(cipher);
    var e=assertThrows(ApiException.class,()->service.open(String.join(".",parts),"login",LoginRequest.class));
    assertFalse(e.getMessage().contains((String)LOGIN.get("password")));
    assertNull(e.getCause());
  }
  @Test void rejectsWrongKeyAndPlaintextAndOversizedEnvelope() throws Exception {
    var other=new AuthPayloadService(factory.getValidator(),clock,pair);
    var payload=encrypt(other.challenge("login"),LOGIN);
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
    assertThrows(ApiException.class,()->service.open("{\"email\":\"fixture\"}","login",LoginRequest.class));
    assertThrows(ApiException.class,()->service.open("a".repeat(16385),"login",LoginRequest.class));
  }
  @Test void validatesDecryptedDtoAndConsumesNonceEvenIfInvalid() throws Exception {
    var payload=encrypt(service.challenge("login"),Map.of("email","not-an-email","password","x"));
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
  }
  @Test void concurrentReplayAllowsOnlyOneRequest() throws Exception {
    var payload=encrypt(service.challenge("login"),LOGIN);
    var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
    try {
      Callable<Boolean> call=()->{start.await();try {service.open(payload,"login",LoginRequest.class);return true;}catch(ApiException e){return false;}};
      var a=pool.submit(call);var b=pool.submit(call);start.countDown();
      assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
    } finally {pool.shutdownNow();}
  }
  @Test void boundsPendingChallengesAndReclaimsExpiredEntries() {
    for(int i=0;i<1024;i++)service.challenge("login");
    assertEquals(429,assertThrows(ApiException.class,()->service.challenge("login")).getStatus());
    clock.now=NOW.plusSeconds(121);assertNotNull(service.challenge("register"));
  }
  @Test void rejectsOtherKeyAlgorithms() throws Exception {
    var c=service.challenge("login");
    var payload=Jwts.builder().header().keyId(c.keyId()).type("doctor-auth+jwe").and()
        .id(c.nonce()).claim("credentials",LOGIN).encryptWith(publicKey(c),Jwts.KEY.RSA_OAEP,Jwts.ENC.A256GCM).compact();
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
  }

  @Test void rejectsCompressionBeforeDecompression() throws Exception {
    var c=service.challenge("login");
    var payload=Jwts.builder().header().keyId(c.keyId()).type("doctor-auth+jwe").and()
        .id(c.nonce()).claim("credentials",LOGIN).compressWith(Jwts.ZIP.DEF)
        .encryptWith(publicKey(c),Jwts.KEY.RSA_OAEP_256,Jwts.ENC.A256GCM).compact();
    assertThrows(ApiException.class,()->service.open(payload,"login",LoginRequest.class));
  }

  @Test void decryptsActualFrontendWebCryptoEnvelope() throws Exception {
    // Cross-language interoperability: actual browser module executed by local Node, no HTTP or database.
    var c=service.challenge("login");
    var module=Path.of("../frontend/src/authEncryption.js").toAbsolutePath().normalize().toUri().toString();
    String script="import {webcrypto} from 'node:crypto';import {readFileSync} from 'node:fs';"
        +"import {encryptCredentials} from '"+module+"';"
        +"const c=JSON.parse(readFileSync(0,'utf8'));"
        +"const e=await encryptCredentials({email:'fixture@example.test',password:'Synthetic-only!123'},c,'login',webcrypto);"
        +"process.stdout.write(e.payload);";
    var child=new ProcessBuilder("node","--input-type=module","-e",script).start();
    try {
      try(var input=child.getOutputStream()){input.write(JsonMapper.builder().build().writeValueAsBytes(c));}
      assertTrue(child.waitFor(15,TimeUnit.SECONDS));
      assertEquals(0,child.exitValue(),"Frontend encryption process failed");
      String payload=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
      assertEquals(LOGIN.get("password"),service.open(payload,"login",LoginRequest.class).getPassword());
    } finally {child.destroy();}
  }
}
