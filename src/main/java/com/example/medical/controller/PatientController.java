package com.example.medical.controller;

import com.example.medical.dto.PageResponse;
import com.example.medical.dto.PatientProfile;
import com.example.medical.dto.RecordDetail;
import com.example.medical.dto.RecordListItem;
import com.example.medical.entity.User;
import com.example.medical.repository.UserRepository;
import com.example.medical.security.AppUserDetails;
import com.example.medical.service.PatientRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientRecordService service;
    private final UserRepository userRepository;

    /** Header thong tin benh nhan. */
    @GetMapping("/me")
    public PatientProfile me(@AuthenticationPrincipal AppUserDetails user) {
        return service.getProfile(user.getId());
    }

    /** Lien ket vi MetaMask cho benh nhan. */
    @PostMapping("/wallet")
    public ResponseEntity<?> linkWallet(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestBody Map<String, String> body) {
        String walletAddress = body.get("walletAddress");
        if (walletAddress == null || !walletAddress.matches("^0x[a-fA-F0-9]{40}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Địa chỉ ví không hợp lệ"));
        }

        Optional<User> existing = userRepository.findByWalletAddressIgnoreCase(walletAddress);
        if (existing.isPresent() && !existing.get().getId().equals(principal.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ví MetaMask này đã được liên kết với tài khoản khác"));
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        user.setWalletAddress(walletAddress.toLowerCase());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Liên kết ví thành công",
                "walletAddress", user.getWalletAddress()
        ));
    }

    /** Bang danh sach ho so + bo loc + phan trang. */
    @GetMapping("/records")
    public PageResponse<RecordListItem> records(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        return service.list(user.getId(), date, specialty, keyword, page, size);
    }

    /** Nut "Xem". */
    @GetMapping("/records/{id}")
    public RecordDetail detail(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return service.detail(user.getId(), id);
    }
}