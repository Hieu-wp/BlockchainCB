package com.example.medical.controller;

import com.example.medical.dto.AccessGrantDto;
import com.example.medical.entity.AccessGrant.GrantStatus;
import com.example.medical.repository.UserRepository;
import com.example.medical.service.AccessGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patient/access-grants")
@RequiredArgsConstructor
public class PatientAccessGrantController {

    private final AccessGrantService grantService;
    private final UserRepository userRepository;

    private Long currentUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"))
                .getId();
    }

    @GetMapping
    public List<AccessGrantDto> list(Authentication auth) {
        return grantService.listForPatient(currentUserId(auth));
    }

    @PostMapping("/{id}/approve")
    public Map<String, String> approve(Authentication auth,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        grantService.updateGrantStatus(currentUserId(auth), id, GrantStatus.ACTIVE, body.get("txHash"));
        return Map.of("message", "Đã cấp quyền truy cập hồ sơ cho bác sĩ");
    }

    @PostMapping("/{id}/reject")
    public Map<String, String> reject(Authentication auth,
                                      @PathVariable Long id) {
        grantService.updateGrantStatus(currentUserId(auth), id, GrantStatus.REJECTED, null);
        return Map.of("message", "Đã từ chối yêu cầu");
    }

    @PostMapping("/{id}/revoke")
    public Map<String, String> revoke(Authentication auth,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        grantService.updateGrantStatus(currentUserId(auth), id, GrantStatus.REVOKED, body.get("txHash"));
        return Map.of("message", "Đã thu hồi quyền truy cập của bác sĩ");
    }
}