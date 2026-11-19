package com.blockchaincb.demo.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class SecurityErrors {
  private SecurityErrors() {}

  private static final JsonMapper JSON = JsonMapper.builder().build();

  public static void write(HttpServletResponse res, int status, String message) throws IOException {
    write(res, status, message, null);
  }

  public static void write(HttpServletResponse res, int status, String message, String code)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter()
        .write(
            JSON.writeValueAsString(
                code == null
                    ? Map.of("status", status, "message", message)
                    : Map.of("status", status, "message", message, "code", code)));
  }
}
