package com.blockchaincb.demo.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded backpressure before JWT database lookup; leaves audit checks intact. */
public class DatabaseRequestGate extends OncePerRequestFilter {
  private final Semaphore permits;
  private final long waitMs;

  public DatabaseRequestGate(int maximum, long waitMs) {
    if (maximum < 0 || waitMs < 0) throw new IllegalArgumentException("Invalid Doctor request limit");
    this.permits = maximum == 0 ? null : new Semaphore(maximum, true);
    this.waitMs = waitMs;
  }

  @Override protected boolean shouldNotFilter(HttpServletRequest req) {
    String path = req.getRequestURI().substring(req.getContextPath().length());
    return permits == null || !(path.startsWith("/api/doctor/") || path.startsWith("/api/auth/doctor/"));
  }

  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    boolean acquired = false;
    try {
      acquired = permits.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
      if (acquired) chain.doFilter(req, res);
      else busy(res);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      busy(res);
    } finally {
      if (acquired) permits.release();
    }
  }

  private void busy(HttpServletResponse res) throws IOException {
    res.setHeader("Retry-After", "2");
    res.setHeader("Cache-Control", "no-store");
    SecurityErrors.write(res, 503, "May chu dang ban; vui long thu lai", "DATABASE_BUSY");
  }
}
