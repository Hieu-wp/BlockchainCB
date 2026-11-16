package com.blockchaincb.demo.security;

import com.blockchaincb.demo.service.AuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class AuditFilter extends OncePerRequestFilter {
  private final AuditService audit;

  public AuditFilter(AuditService audit) {
    this.audit = audit;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof DoctorPrincipal actor)) {
      chain.doFilter(req, res);
      return;
    }
    // Do not store query strings, body, tokens or untrusted resource names.
    String path = req.getRequestURI().substring(req.getContextPath().length());
    String resource = path.matches("/api/doctor/[a-z0-9/-]+") ? path : "/api/doctor/unknown";
    if (resource.length() > 200) resource = "/api/doctor/unknown";
    try {
      audit.record(actor.userId(), req.getMethod(), resource, "ATTEMPT");
    } catch (RuntimeException unavailable) {
      SecurityErrors.write(res, 503, "Nhat ky tam thoi khong kha dung");
      return;
    }
    var buffered = new ContentCachingResponseWrapper(res);
    boolean failed = true;
    try {
      chain.doFilter(req, buffered);
      failed = false;
    } finally {
      try {
        audit.record(
            actor.userId(),
            req.getMethod(),
            resource,
            failed || buffered.getStatus() >= 500
                ? "ERROR"
                : buffered.getStatus() >= 400 ? "DENIED" : "SUCCESS");
      } catch (RuntimeException unavailable) {
        buffered.resetBuffer();
        buffered.setStatus(503);
        SecurityErrors.write(buffered, 503, "Nhat ky tam thoi khong kha dung");
      }
      buffered.copyBodyToResponse();
    }
  }
}
