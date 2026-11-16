package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.service.HashService;
import org.junit.jupiter.api.Test;

class HashServiceTest {
  HashService hash = new HashService();

  @Test
  void matchesPublishedSha256Vector() {
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash.sha256("abc"));
  }

  @Test
  void canonicalNormalizesUnicodeAndNewlines() {
    assertEquals(hash.canonical("é", "a\nb"), hash.canonical("e\u0301", "a\r\nb"));
    assertEquals("2:é1:x", hash.canonical("é", "x"));
  }

  @Test
  void fieldsCannotCollideByConcatenation() {
    assertNotEquals(hash.sha256(hash.canonical("ab", "c")), hash.sha256(hash.canonical("a", "bc")));
    assertNotEquals(hash.sha256(hash.canonical("a", 1)), hash.sha256(hash.canonical("a", 2)));
  }
}
