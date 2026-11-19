package com.blockchaincb.demo.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

class JwtDatabaseFailureTest {
  @Test void exhaustedPoolReturns503WithoutCallingHandlerOrLeakingStackTrace() throws Exception {
    var jwt=mock(JwtService.class);var identities=mock(DoctorIdentityService.class);
    when(jwt.extractUserId("fixture")).thenReturn(1L);
    when(identities.load(1L)).thenThrow(new CannotCreateTransactionException("PRIVATE_CONNECTION_DETAIL"));
    var request=new MockHttpServletRequest("GET","/api/doctor/me");request.addHeader("Authorization","Bearer fixture");
    var response=new MockHttpServletResponse();
    try {
      new JwtAuthenticationFilter(jwt,identities).doFilter(request,response,(q,s)->fail("No authenticated DB access"));
      assertEquals(503,response.getStatus());assertTrue(response.getContentAsString().contains("DATABASE_UNAVAILABLE"));
      assertFalse(response.getContentAsString().contains("PRIVATE_CONNECTION_DETAIL"));
      assertNull(SecurityContextHolder.getContext().getAuthentication());
    }finally{SecurityContextHolder.clearContext();}
  }
}
