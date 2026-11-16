package com.blockchaincb.demo.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityErrors {
  private SecurityErrors() {}

  public static void write(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
  }
}
