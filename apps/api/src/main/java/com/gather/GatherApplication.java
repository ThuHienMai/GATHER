package com.gather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatherApplication {
  public static void main(String[] args) {
    SpringApplication.run(GatherApplication.class, args);
  }
}
