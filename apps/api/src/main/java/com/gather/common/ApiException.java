package com.gather.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiException extends ResponseStatusException {
  public ApiException(int status, String detail) {
    super(HttpStatus.valueOf(status), detail);
  }
}
