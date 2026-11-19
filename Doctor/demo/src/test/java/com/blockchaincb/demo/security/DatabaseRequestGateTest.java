package com.blockchaincb.demo.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class DatabaseRequestGateTest {
  @Test void twoRequestsCanRunTogetherWhileTheThirdIsBounded() throws Exception {
    var gate=new DatabaseRequestGate(2,0);var entered=new CountDownLatch(2);var leave=new CountDownLatch(1);
    var pool=Executors.newFixedThreadPool(2);
    try {
      Callable<Void> call=()->{
        gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/me"),new MockHttpServletResponse(),(q,s)->{
          entered.countDown();try {if(!leave.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout");}
          catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        });return null;
      };
      var first=pool.submit(call);var second=pool.submit(call);
      assertTrue(entered.await(5,TimeUnit.SECONDS));
      var third=new MockHttpServletResponse();
      gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/wallet"),third,(q,s)->fail("Exceeded two requests"));
      assertEquals(503,third.getStatus());
      leave.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
    }finally{leave.countDown();pool.shutdownNow();}
  }

  @Test void boundsConcurrentRequestsAndReleasesAfterSuccess() throws Exception {
    var gate=new DatabaseRequestGate(1,0);var entered=new CountDownLatch(1);var leave=new CountDownLatch(1);
    var pool=Executors.newSingleThreadExecutor();
    try {
      var first=pool.submit(()->{
        try {gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/me"),new MockHttpServletResponse(),(q,s)->{
          entered.countDown();try {if(!leave.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout");}
          catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        });}catch(Exception e){throw new RuntimeException(e);}
      });
      assertTrue(entered.await(5,TimeUnit.SECONDS));var busy=new MockHttpServletResponse();
      gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/wallet"),busy,(q,s)->fail("Must not reach DB"));
      assertEquals(503,busy.getStatus());assertEquals("2",busy.getHeader("Retry-After"));
      assertTrue(busy.getContentAsString().contains("DATABASE_BUSY"));
      leave.countDown();first.get(5,TimeUnit.SECONDS);
      var free=new MockHttpServletResponse();
      gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/me"),free,(q,s)->s.getWriter().write("ok"));
      assertEquals("ok",free.getContentAsString());
    }finally{leave.countDown();pool.shutdownNow();}
  }
  @Test void exceptionDoesNotLeakRequestPermit() throws Exception {
    var gate=new DatabaseRequestGate(1,0);
    assertThrows(jakarta.servlet.ServletException.class,()->gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/me"),
        new MockHttpServletResponse(),(q,s)->{throw new jakarta.servlet.ServletException("fixture");}));
    gate.doFilter(new MockHttpServletRequest("GET","/api/doctor/me"),new MockHttpServletResponse(),(q,s)->{});
  }
  @Test void doesNotGateOtherModules() throws Exception {
    var gate=new DatabaseRequestGate(1,0);var response=new MockHttpServletResponse();
    gate.doFilter(new MockHttpServletRequest("GET","/api/patient/me"),response,(q,s)->s.getWriter().write("untouched"));
    assertEquals("untouched",response.getContentAsString());
  }
}
