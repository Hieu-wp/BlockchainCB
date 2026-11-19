package com.blockchaincb.demo.exception;

public class ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public ApiException(int status, String message) {
    this(status, message != null && message.matches("[A-Z_]{3,80}") ? message : null, message);
  }

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
