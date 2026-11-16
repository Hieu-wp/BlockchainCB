package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private final String secret = Base64.getEncoder().encodeToString(new byte[32]);
  private final JwtService jwt = new JwtService(secret);

  @Test
  void signsUserIdentityAndOneHourExpiry() {
    var u = new User();
    u.setId(42L);
    var d = new Doctor();
    d.setUser(u);
    var c = jwt.extractClaims(jwt.generateToken(d));
    assertEquals("42", c.getSubject());
    assertEquals(42L, jwt.extractUserId(jwt.generateToken(d)));
    assertEquals(3600000, c.getExpiration().getTime() - c.getIssuedAt().getTime());
  }

  @Test
  void rejectsExpiredMalformedAndWrongKey() {
    var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    String expired =
        Jwts.builder()
            .subject("42")
            .issuer("blockchaincb-doctor")
            .audience()
            .add("doctor-api")
            .and()
            .expiration(new Date(System.currentTimeMillis() - 10000))
            .signWith(key)
            .compact();
    assertFalse(jwt.isTokenValid(expired));
    assertFalse(jwt.isTokenValid("broken"));
    assertFalse(jwt.isTokenValid(null));
    var bytes = new byte[32];
    Arrays.fill(bytes, (byte) 7);
    assertFalse(
        jwt.isTokenValid(
            Jwts.builder().subject("42").signWith(Keys.hmacShaKeyFor(bytes)).compact()));
  }

  @Test
  void rejectsTokenFromDifferentIssuerOrAudience() {
    var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    assertFalse(
        jwt.isTokenValid(
            Jwts.builder().issuer("patient-api").subject("42").signWith(key).compact()));
  }
}
