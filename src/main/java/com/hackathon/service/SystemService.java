package com.hackathon.service;

import com.hackathon.dto.KeepAliveResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SystemService {

    private final JdbcTemplate jdbcTemplate;

    public SystemService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KeepAliveResponse getKeepAliveStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new KeepAliveResponse(
                    "UP",
                    "CONNECTED",
                    "BD — As you can see, I'm still alive! ",
                    Instant.now().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new KeepAliveResponse(
                    "UP",
                    "DISCONNECTED",
                    "B — As you can see, I'm still alive! ",
                    Instant.now().toString());
        }
    }
}
