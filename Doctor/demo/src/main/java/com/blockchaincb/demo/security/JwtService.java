package com.blockchaincb.demo.security;

import com.blockchaincb.demo.entity.Doctor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey secretKey;

  private static final long EXPIRATION = 60 * 60 * 1000L;

  public JwtService(@Value("${jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  // tao thong tin jwT cua doctor
  public String generateToken(Doctor doctor) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + EXPIRATION);

    return Jwts.builder()
        .subject(doctor.getUser().getId().toString())
        .issuer("blockchaincb-doctor")
        .audience()
        .add("doctor-api")
        .and()
        .claim("doctorId", doctor.getId())
        .issuedAt(now)
        .expiration(expiration)
        .signWith(secretKey)
        .compact();
  }

  // lay thong tin tu jwt
  public Claims extractClaims(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer("blockchaincb-doctor")
            .requireAudience("doctor-api")
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (claims.getExpiration() == null)
      throw new io.jsonwebtoken.MalformedJwtException("Missing expiration");
    return claims;
  }

  public Long extractUserId(String token) {
    return Long.valueOf(extractClaims(token).getSubject());
  }

  public boolean isTokenValid(String token) {
    try {
      extractClaims(token);
      return true;
    } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
