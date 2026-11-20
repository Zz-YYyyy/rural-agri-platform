package com.ruralagri.platform.config;

import com.ruralagri.platform.common.web.JsonErrorWriter;
import com.ruralagri.platform.auth.BearerSessionFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonErrorWriter errorWriter,
                                           Environment environment,
                                           ObjectProvider<BearerSessionFilter> bearerFilters) throws Exception {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("bootstrap", "dev"));
        BearerSessionFilter bearerFilter = bearerFilters.getIfAvailable();
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/assets/default-avatar.svg").permitAll();
                    if (localProfile) {
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll();
                    }
                    if (bearerFilter != null) {
                        authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                                .permitAll();
                        authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();
                        authorize.requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated();
                    }
                    // No other business endpoint is opened until its authorization is implemented.
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(request, response, 401, "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(request, response, 403, "FORBIDDEN", "无权执行此操作")));
        if (bearerFilter != null) {
            http.addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}
