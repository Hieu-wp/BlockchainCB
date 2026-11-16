package com.blockchaincb.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Canonical v1: ordered length-prefixed UTF-8 fields; no locale dependent formatting. */
@Service
public class HashService {
  public static final String SCHEMA = "doctor-record-v1";

  public String normalize(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
          throw new com.blockchaincb.demo.exception.ApiException(400, "Unicode khong hop le");
      } else if (Character.isLowSurrogate(c)) {
        throw new com.blockchaincb.demo.exception.ApiException(400, "Unicode khong hop le");
      }
    }
    return Normalizer.normalize(
        text.replace("\r\n", "\n").replace("\r", "\n"), Normalizer.Form.NFC);
  }

  public String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public String sha256(String content) {
    return sha256(content.getBytes(StandardCharsets.UTF_8));
  }

  public String canonical(Object... fields) {
    StringBuilder out = new StringBuilder();
    for (Object field : fields) {
      String value = field == null ? "" : normalize(field.toString());
      out.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
    }
    return out.toString();
  }
}
