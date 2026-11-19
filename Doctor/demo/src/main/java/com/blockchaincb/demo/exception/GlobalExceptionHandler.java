package com.blockchaincb.demo.exception;

import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler({org.springframework.transaction.CannotCreateTransactionException.class,
      org.springframework.transaction.TransactionTimedOutException.class,
      org.springframework.dao.DataAccessResourceFailureException.class,
      org.springframework.dao.TransientDataAccessResourceException.class,
      org.springframework.dao.QueryTimeoutException.class})
  ResponseEntity<?> databaseUnavailable(Exception e) {
    return ResponseEntity.status(503).header("Retry-After", "2")
        .body(Map.of("status", 503, "code", "DATABASE_UNAVAILABLE",
            "message", "Ket noi database tam thoi khong kha dung"));
  }
  private ResponseEntity<?> error(int status, String message) {
    return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    if (e.getCode() != null)
      return ResponseEntity.status(e.getStatus())
          .body(Map.of("status", e.getStatus(), "code", e.getCode(), "message", e.getMessage()));
    return error(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<?> validation(Exception e) {
    return error(400, "Du lieu yeu cau khong hop le");
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class,
    jakarta.persistence.OptimisticLockException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return error(409, "Du lieu trung hoac da thay doi; vui long tai lai");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(Exception e) {
    return error(403, "Khong du quyen");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> large(Exception e) {
    return error(413, "Tep qua lon");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(Exception e) {
    return error(405, "Phuong thuc khong duoc ho tro");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> media(Exception e) {
    return error(415, "Content-Type khong duoc ho tro");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<?> missing(Exception e) {
    return error(404, "Khong tim thay tai nguyen");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    return error(500, "Loi xu ly noi bo");
  }
}
