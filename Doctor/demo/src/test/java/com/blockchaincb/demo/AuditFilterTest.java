package com.blockchaincb.demo;

import com.blockchaincb.demo.security.AuditFilter;
import com.blockchaincb.demo.security.DoctorPrincipal;
import com.blockchaincb.demo.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditFilterTest {
  AuditService audit=mock(AuditService.class);
  @BeforeEach void identity() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new DoctorPrincipal(1L,2L),null,java.util.List.of()));
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }
  @Test void unavailableAuditPreventsOperation() throws Exception {
    doThrow(new IllegalStateException("offline")).when(audit).record(anyLong(),anyString(),anyString(),anyString());
    var request=new MockHttpServletRequest("GET","/api/doctor/records/1");
    var response=new MockHttpServletResponse();
    new AuditFilter(audit).doFilter(request,response,(req,res) -> fail("Operation must not run without audit"));
    assertEquals(503,response.getStatus());
    assertTrue(response.getContentAsString().contains("AUDIT_UNAVAILABLE"));
  }
  @Test void failedOutcomeAuditDoesNotReleaseMedicalResponse() throws Exception {
    doNothing().doThrow(new IllegalStateException("offline")).when(audit).record(anyLong(),anyString(),anyString(),anyString());
    var request=new MockHttpServletRequest("GET","/api/doctor/records/1");
    var response=new MockHttpServletResponse();
    new AuditFilter(audit).doFilter(request,response,(req,res) -> res.getWriter().write("PRIVATE_DIAGNOSIS"));
    assertEquals(503,response.getStatus());
    assertFalse(response.getContentAsString().contains("PRIVATE_DIAGNOSIS"));
    verify(audit,times(2)).record(anyLong(),anyString(),anyString(),anyString());
  }
}
