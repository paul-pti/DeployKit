package com.deploykit.controller;

import com.deploykit.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness endpoint for the dashboard. Database readiness is exposed
 * separately by Actuator at /actuator/health.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return HealthResponse.up();
    }
}
