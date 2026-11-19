package com.blockchaincb.demo.blockchain;

import static org.junit.jupiter.api.Assertions.*;
import com.blockchaincb.demo.exception.ApiException;
import org.junit.jupiter.api.Test;

class WalletOriginPolicyTest {
  WalletOriginPolicy policy(String domain,String allowed) {
    return new WalletOriginPolicy(new ChainSettings(false,false,"",domain,3),allowed);
  }
  @Test void supportsExactLoopbackAliasesOnSamePortOnly() {
    var p=policy("localhost:5173","");
    assertEquals("http://localhost:5173",p.primaryOrigin());
    assertEquals("http://127.0.0.1:5173",p.requireAllowed("http://127.0.0.1:5173"));
    assertEquals("http://[::1]:5173",p.requireAllowed("http://[::1]:5173"));
    assertThrows(ApiException.class,()->p.requireAllowed("http://localhost:4173"));
    assertThrows(ApiException.class,()->p.requireAllowed("https://localhost:5173"));
    assertThrows(ApiException.class,()->p.requireAllowed("http://localhost.attacker.example:5173"));
  }
  @Test void productionDomainDoesNotImplicitlyTrustLocalhostOrOtherDomains() {
    var p=policy("doctor.example","");
    assertEquals("https://doctor.example",p.requireAllowed("https://doctor.example"));
    assertThrows(ApiException.class,()->p.requireAllowed("http://localhost:5173"));
    assertThrows(ApiException.class,()->p.requireAllowed("http://doctor.example"));
  }
  @Test void acceptsExplicitOriginListWithoutWildcardAndRejectsBadConfig() {
    var p=policy("localhost:5173","http://localhost:5173,https://doctor.example");
    assertEquals(2,p.allowedOrigins().size());
    assertThrows(ApiException.class,()->p.requireAllowed("http://127.0.0.1:5173"));
    for(String bad:new String[]{"*","http://doctor.example","https://doctor.example/path","https://user@doctor.example",
        "https://doctor.example?x=1","https://doctor.example#x","https://doctor.example\n","http://localhost:0"})
      assertThrows(IllegalArgumentException.class,()->policy("localhost:5173",bad));
  }
  @Test void allowsFullOriginDomainConfigAndRejectsUntrustedRequestPaths() {
    var p=policy("http://127.0.0.1:5173","");
    assertEquals("127.0.0.1:5173",p.primaryDomain());
    assertThrows(ApiException.class,()->p.requireAllowed("http://127.0.0.1:5173/"));
    assertThrows(ApiException.class,()->p.requireAllowed(null));
  }
}
