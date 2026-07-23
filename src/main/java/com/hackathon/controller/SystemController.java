package com.hackathon.controller;

import com.hackathon.dto.AiBatchMetricsResponse;
import com.hackathon.dto.KeepAliveResponse;
import com.hackathon.service.ParticipantAiBatchProcessingService;
import com.hackathon.service.SystemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;
    private final ParticipantAiBatchProcessingService batchService;

    public SystemController(SystemService systemService,
                            ParticipantAiBatchProcessingService batchService) {
        this.systemService = systemService;
        this.batchService = batchService;
    }

    @GetMapping("/keep-alive")
    public KeepAliveResponse keepAlive() {
        return systemService.getKeepAliveStatus();
    }

    @GetMapping("/ai-batch/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public AiBatchMetricsResponse aiBatchMetrics() {
        return batchService.getMetrics();
    }
}
