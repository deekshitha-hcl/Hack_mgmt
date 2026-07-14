package com.hackathon.controller;

import com.hackathon.service.SystemService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemControllerTest {

    @Test
    void keepAliveEndpointReturnsKeepAlivePayload() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SystemController(new SystemService(new StubJdbcTemplate()))).build();

        mockMvc.perform(get("/api/system/keep-alive"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("CONNECTED"))
                .andExpect(jsonPath("$.message").value("BD — As you can see, I'm still alive! "))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private static class StubJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return requiredType.cast(Integer.valueOf(1));
        }
    }
}
