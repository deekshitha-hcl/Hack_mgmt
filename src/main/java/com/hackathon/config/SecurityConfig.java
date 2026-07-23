package com.hackathon.config;

import com.hackathon.security.CustomUserDetailsService;
import com.hackathon.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf().disable()
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/participants/register",
                            "/api/participants/check-in/qr",
                            "/api/participants/check-in/verify",
                                "/api/panelists/register/**",
                                "/api/panelists/invite/validate/**",
                                "/api/system/keep-alive",
                                "/uploads/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*")
                        .permitAll()
                            .requestMatchers(
                                "/api/dashboard/panelist/me",
                                "/api/dashboard/panelist/*")
                            .hasAnyRole("ADMIN", "PANELIST")
                            .requestMatchers("/api/dashboard/summary")
                            .hasRole("ADMIN")
                        .requestMatchers(
                                "/api/auth/register")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/squads/event/*")
                        .hasAnyRole("ADMIN", "PANELIST")
                        .requestMatchers(HttpMethod.GET, "/api/squads/*/members")
                        .hasAnyRole("ADMIN", "PANELIST")
                        .requestMatchers("/api/squads/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/panelists", "/api/panelists/invite/active")
                        .hasAnyRole("ADMIN", "PANELIST")
                        .requestMatchers(HttpMethod.POST, "/api/panelists/invite")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/panelists/*")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/events")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/events/*")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/*")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                "/api/participants/**",
                                "/api/feedback/**")
                        .hasAnyRole("ADMIN", "PANELIST")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
