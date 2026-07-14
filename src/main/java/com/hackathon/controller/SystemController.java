package com.hackathon.controller;

import com.hackathon.dto.KeepAliveResponse;
import com.hackathon.service.SystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/keep-alive")
    public KeepAliveResponse keepAlive() {
        return systemService.getKeepAliveStatus();
    }
}
