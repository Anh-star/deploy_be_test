package com.cmcu.itstudy.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.cmcu.itstudy.security.JwtAuthenticationFilter;
import com.cmcu.itstudy.security.OAuth2SuccessHandler;
import com.cmcu.itstudy.security.RestAccessDeniedHandler;
import com.cmcu.itstudy.security.RestAuthenticationEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            OAuth2SuccessHandler oAuth2SuccessHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        ).authenticated()
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/homepage/**",
                                "/api/leaderboard",
                                "/api/documents",
                                "/api/documents/*",
                                "/api/documents/*/view",
                                "/api/documents/*/preview",
                                "/api/categories",
                                "/api/tags/popular",
                                "/api/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/notifications/subscribe"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comments/*/replies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/community/posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/community/posts/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/community/posts/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/community/posts/comments/*/replies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/quizzes/ai-import").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auto-quiz/generations/*/source").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auto-quiz/generations/*/complete").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auto-quiz/generations/*/reject").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auto-quiz/generations/*/fail").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/return").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/ipn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                    .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "X-Refresh-Token"));
        configuration.setExposedHeaders(Arrays.asList(
                "X-Preview-Mode",
                "X-Preview-Pages",
                "X-Total-Pages",
                "X-Preview-Renderer"
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

