package com.hackathon.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {

    private final String apiServerUrl;

    public OpenApiConfig(@Value("${app.api-cors-url}") String apiServerUrl) {
        System.out.println("api cors url in openapi config: " + apiServerUrl);
        this.apiServerUrl = apiServerUrl;
    }

    @Bean
    public OpenAPI recruitmentEventOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Recruitment Event Platform API")
                        .version("v1")
                        .description("Demo-ready backend for recruitment events, participants, squads, feedback, and email logs."))
                .servers(List.of(new Server().url(apiServerUrl).description("Configured server")))
                .components(new Components().addSecuritySchemes("Bearer Authentication",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .security(List.of(new SecurityRequirement().addList("Bearer Authentication")));
    }
}
