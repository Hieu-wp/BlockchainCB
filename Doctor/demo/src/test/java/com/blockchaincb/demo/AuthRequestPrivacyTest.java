package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.dto.LoginRequest;
import com.blockchaincb.demo.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Pure serialization tests: no Spring context, database or real credentials. */
class AuthRequestPrivacyTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void loginAcceptsPasswordButNeverSerializesIt() {
    var request = mapper.readValue(
        "{\"email\":\"fixture@example.test\",\"password\":\"Test-only!123\"}", LoginRequest.class);
    assertEquals("Test-only!123", request.getPassword());
    var json = mapper.readTree(mapper.writeValueAsString(request));
    assertFalse(json.has("password"));
    assertEquals("fixture@example.test", json.get("email").asText());
  }

  @Test
  void registrationAcceptsPasswordButNeverSerializesIt() {
    var request = mapper.readValue(
        "{\"email\":\"fixture@example.test\",\"password\":\"Test-only!123\"}", RegisterRequest.class);
    assertEquals("Test-only!123", request.getPassword());
    var json = mapper.readTree(mapper.writeValueAsString(request));
    assertFalse(json.has("password"));
    assertEquals("fixture@example.test", json.get("email").asText());
  }
}
