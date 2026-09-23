package com.gather.common;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail api(ResponseStatusException e) {
    return ProblemDetail.forStatusAndDetail(
        e.getStatusCode(), e.getReason() == null ? "Request failed" : e.getReason());
  }

  @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
  ProblemDetail conflict(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.PRECONDITION_FAILED,
        "This event changed. Review the latest version before saving again.");
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    java.time.DateTimeException.class
  })
  ProblemDetail invalid(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid request. Check the supplied values.");
  }
}
