package com.example.hotel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security配置类
 * 配置API访问权限和安全策略
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF，因为这是一个REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // 配置请求授权
            .authorizeHttpRequests(authz -> authz
                // 允许所有人访问API端点（开发阶段）
                .requestMatchers("/api/**").permitAll()
                
                // 允许访问actuator健康检查端点
                .requestMatchers("/actuator/health").permitAll()
                
                // 允许访问Swagger文档（如果有的话）
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // 允许访问静态资源
                .requestMatchers("/static/**", "/css/**", "/js/**", "/images/**").permitAll()
                
                // 允许访问HTML文件（直接放在static目录下的文件）
                .requestMatchers("/*.html", "/*.css", "/*.js").permitAll()
                
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            
            // 配置HTTP Basic认证（可选）
            .httpBasic(httpBasic -> httpBasic
                .realmName("Hotel Management System")
            )
            
            // 配置安全头
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            );

        return http.build();
    }
} 