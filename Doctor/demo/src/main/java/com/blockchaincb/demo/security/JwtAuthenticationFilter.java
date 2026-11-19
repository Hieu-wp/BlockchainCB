package com.blockchaincb.demo.security;

import com.blockchaincb.demo.exception.ApiException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final DoctorIdentityService identities;

  public JwtAuthenticationFilter(JwtService jwt, DoctorIdentityService identities) {
    this.jwt = jwt;
    this.identities = identities;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return !req.getRequestURI().substring(req.getContextPath().length()).startsWith("/api/doctor/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null) {
      try {
        if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
        var d = identities.load(jwt.extractUserId(header.substring(7)));
        var auth =
            new UsernamePasswordAuthenticationToken(
                new DoctorPrincipal(d.getUser().getId(), d.getId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (ApiException e) {
        SecurityContextHolder.clearContext();
        SecurityErrors.write(res, e.getStatus(), e.getMessage(), e.getCode());
        return;
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
        SecurityErrors.write(res, 401, "Token khong hop le hoac het han", "TOKEN_INVALID");
        return;
      } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
        SecurityContextHolder.clearContext();
        res.setHeader("Retry-After", "2");
        SecurityErrors.write(res, 503, "Ket noi database tam thoi khong kha dung", "DATABASE_UNAVAILABLE");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
