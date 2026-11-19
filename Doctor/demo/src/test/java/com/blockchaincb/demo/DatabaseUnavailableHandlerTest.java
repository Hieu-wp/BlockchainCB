package com.blockchaincb.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DatabaseUnavailableHandlerTest {
  @RestController static class Fixture {
    @GetMapping("/fixture") public String read() { throw new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAIL"); }
  }
  @Test void unavailableDbIs503WithoutSensitiveException() throws Exception {
    MockMvcBuilders.standaloneSetup(new Fixture())
        .setControllerAdvice(new com.blockchaincb.demo.exception.GlobalExceptionHandler()).build()
        .perform(get("/fixture")).andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After","2"))
        .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_DATABASE_DETAIL"))));
  }
}
