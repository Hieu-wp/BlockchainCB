package com.blockchaincb.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Chỉ chuẩn hóa lỗi nghiệp vụ Admin; không đưa SQL/stack trace/thông tin kết nối vào response. */
@RestControllerAdvice(assignableTypes = {AdminController.class, AdminAuthController.class,
        AdminUserController.class, AdminDoctorController.class, AdminAuditLogController.class})
public class AdminExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> xuLyLoiNghiepVu(ResponseStatusException loi) {
        ProblemDetail chiTiet = ProblemDetail.forStatusAndDetail(loi.getStatusCode(),
                loi.getReason() == null ? "Yêu cầu không thể hoàn tất" : loi.getReason());
        // Frontend hiện kiểm tra application/json; không đổi sang problem+json.
        return ResponseEntity.status(loi.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(chiTiet);
    }
}
