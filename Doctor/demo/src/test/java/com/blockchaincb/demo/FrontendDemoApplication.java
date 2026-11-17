package com.blockchaincb.demo;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.repository.*;
import com.blockchaincb.demo.security.DoctorPrincipal;
import com.blockchaincb.demo.service.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit test-classpath launcher. Never packaged into the production JAR. */
public class FrontendDemoApplication {
  public static void main(String[] args) {
    String password = System.getenv("DOCTOR_DEMO_PASSWORD");
    if (password == null
        || password.length() < 8
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Set DOCTOR_DEMO_PASSWORD (8+ characters, <=72 UTF-8 bytes) for isolated demo only");
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    SpringApplication app = new SpringApplication(DemoApplication.class, Fixture.class);
    app.addInitializers(
        context -> {
          Map<String, Object> p = new HashMap<>();
          p.put("jwt.secret", Base64.getEncoder().encodeToString(key));
          p.put("server.address", "127.0.0.1");
          p.put("server.port", "8081");
          p.put("spring.sql.init.mode", "always");
          p.put(
              "spring.sql.init.schema-locations",
              "classpath:db/local-shared-schema.sql,classpath:db/local-doctor-schema.sql,classpath:db/local-blockchain-schema.sql");
          p.put("spring.sql.init.data-locations", "classpath:db/local-specialties.sql");
          p.put("spring.jpa.hibernate.ddl-auto", "validate");
          p.put("spring.jpa.show-sql", "false");
          p.put("doctor.storage.local-enabled", "true");
          p.put("doctor.storage.local-root", "./target/frontend-demo-files");
          p.put("debug", "false");
          p.put("logging.level.org.springframework", "WARN");
          p.put("logging.level.org.hibernate", "WARN");
          p.put("logging.level.com.blockchaincb", "INFO");
          context
              .getEnvironment()
              .getPropertySources()
              .addFirst(new MapPropertySource("isolatedFrontendDemo", p));
        });
    app.run(args);
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>
        fixtureMarker() {
      var bean =
          new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
      bean.setFilter(
          (req, res, chain) -> {
            ((jakarta.servlet.http.HttpServletResponse) res)
                .setHeader("X-Doctor-Fixture", "isolated-h2");
            chain.doFilter(req, res);
          });
      bean.setOrder(Integer.MIN_VALUE);
      return bean;
    }

    @Bean
    DataSource dataSource() {
      var ds = new DriverManagerDataSource();
      ds.setDriverClassName("org.h2.Driver");
      ds.setUrl("jdbc:h2:mem:frontend_demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
      ds.setUsername("sa");
      ds.setPassword("");
      return ds;
    }

    @Bean
    CommandLineRunner seed(
        AuthService auth,
        JdbcTemplate db,
        UserRepository users,
        PatientRepository patients,
        AccessGrantRepository grants,
        MedicalRecordService records) {
      return args -> {
        if (!db.queryForObject("SELECT H2VERSION()", String.class).startsWith("2."))
          throw new IllegalStateException("H2 required");
        Doctor approved = null;
        for (String state : List.of("APPROVED", "PENDING", "REJECTED", "BLOCKED")) {
          var r = new RegisterRequest();
          r.setEmail(state.toLowerCase() + "@doctor.local");
          r.setPassword(System.getenv("DOCTOR_DEMO_PASSWORD"));
          r.setFullName("Nguyễn Minh Anh");
          r.setHospital("Bệnh viện Đa khoa Demo");
          r.setLicenseNumber("LOCAL-" + state);
          r.setSpecialtyId(2L);
          Doctor d = auth.register(r);
          db.update("UPDATE public.doctors SET approval_status=? WHERE id=?", state, d.getId());
          if (state.equals("APPROVED")) approved = d;
        }
        var principal = new DoctorPrincipal(approved.getUser().getId(), approved.getId());
        int index = 0;
        for (String name : List.of("Trần Thanh Hà", "Lê Hoàng Nam", "Bệnh nhân không cấp quyền")) {
          index++;
          var u = new User();
          u.setEmail("patient" + index + "@doctor.local");
          u.setFullName(name);
          u.setRole("PATIENT");
          u.setPasswordHash("LOCAL_FIXTURE_NO_LOGIN");
          users.saveAndFlush(u);
          var p = new Patient();
          p.setUserId(u.getId());
          patients.saveAndFlush(p);
          if (index == 3) continue;
          var g = new AccessGrant();
          g.setPatientId(p.getId());
          g.setDoctorId(approved.getId());
          g.setCanRead(true);
          g.setCanCreate(true);
          g.setCanUpdate(true);
          g.setValidFrom(Instant.now().minusSeconds(60));
          grants.saveAndFlush(g);
          var body =
              new MedicalRecordRequest(
                  p.getId(),
                  LocalDate.now(),
                  "Nội dung chẩn đoán minh họa — không phải dữ liệu thật",
                  "Đơn thuốc minh họa",
                  "Kết quả xét nghiệm minh họa",
                  "Dữ liệu cách ly phục vụ demo",
                  List.of());
          var record = records.create(principal, body);
          if (index == 1) records.update(principal, record.id(), new UpdateRecordRequest(1, body));
          else {
            g.setCanCreate(false);
            g.setCanUpdate(false);
            grants.saveAndFlush(g);
          }
        }
        System.out.println(
            "ISOLATED H2 FRONTEND DEMO READY on 127.0.0.1:8081. No Supabase connection.");
      };
    }
  }
}
