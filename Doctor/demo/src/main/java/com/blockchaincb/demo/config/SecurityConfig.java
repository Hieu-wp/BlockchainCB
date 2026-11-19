package com.blockchaincb.demo.config;

import com.blockchaincb.demo.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwt,
      DoctorIdentityService identities,
      com.blockchaincb.demo.service.AuditService audit,
      @org.springframework.beans.factory.annotation.Value("${doctor.http.max-concurrent-requests:0}") int maximumRequests,
      @org.springframework.beans.factory.annotation.Value("${doctor.http.queue-wait-ms:2000}") long queueWaitMs)
      throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.POST, "/api/auth/doctor/register", "/api/auth/doctor/login",
                        "/api/auth/doctor/register/encrypted", "/api/auth/doctor/login/encrypted")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/doctor/encryption-challenge")
                    .permitAll()
                    .requestMatchers("/api/doctor/**")
                    .hasRole("DOCTOR")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            SecurityErrors.write(
                                res, 401, "Can xac thuc", "AUTHENTICATION_REQUIRED"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            SecurityErrors.write(res, 403, "Khong du quyen", "ACCESS_DENIED")))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwt, identities),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new AuditFilter(audit), JwtAuthenticationFilter.class)
        .addFilterBefore(new DatabaseRequestGate(maximumRequests, queueWaitMs), JwtAuthenticationFilter.class)
        .build();
  }
}
