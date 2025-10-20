package com.technicalchallenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
@Bean
public CorsFilter corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    
    // Allow credentials (like cookies or Basic Auth headers)
    config.setAllowCredentials(true); 
    
    // 💡 Match front-end URL(s). 
    config.addAllowedOrigin("http://localhost:3000"); 
    config.addAllowedOrigin("http://localhost:5173"); 
    
    config.addAllowedHeader("*"); // Allow all headers
    config.addAllowedMethod("*"); // Allow all methods (GET, POST, OPTIONS, etc.)
    
    // Apply this configuration to ALL API paths
    source.registerCorsConfiguration("/**", config); 
    return new CorsFilter(source);
}
    
@Bean
public SecurityFilterChain securityFilterChain(
        HttpSecurity http, 
        HandlerMappingIntrospector introspector) throws Exception {

    MvcRequestMatcher.Builder mvcMatcherBuilder = new MvcRequestMatcher.Builder(introspector);
            
    http
        .cors(cors -> {})
                
        .csrf(AbstractHttpConfigurer::disable)
            
        // Authorization Rules
        .authorizeHttpRequests(auth -> auth

            .requestMatchers(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.toString())).permitAll()

            .requestMatchers(
                new AntPathRequestMatcher("/api-docs/**"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/h2-console/**") 
                ).permitAll()

                // Main API endpoints requiring authentication (MVC Matcher)    
                .requestMatchers(mvcMatcherBuilder.pattern("/api/**"))
                .authenticated()
                
                // All other requests MUST be authenticated
                .anyRequest().authenticated()
            )
            
            // Allow frames for H2 Console
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            
            // Configure how authentication happens
            .httpBasic(httpBasic -> {}); // Uses Basic Authentication header (Username/Password)

        return http.build();
    }

    // Password Encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}