package com.chegg.ratelimiter.controller;

import com.chegg.ratelimiter.dto.StatusResponse;
import com.chegg.ratelimiter.service.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/general")
    public ResponseEntity<Map<String, String>> general() {
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, String>> submit() {
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(HttpServletRequest request) {
        RateLimitResult result = (RateLimitResult) request.getAttribute(RateLimitResult.REQUEST_ATTRIBUTE);
        return ResponseEntity.ok(new StatusResponse(result.limit(), result.remaining(), result.resetAt()));
    }
}
