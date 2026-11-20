package com.ruralagri.platform.auth;

import java.time.Clock;

import com.ruralagri.platform.auth.persistence.AccountMapper;
import com.ruralagri.platform.auth.persistence.MyBatisAccountRepository;
import com.ruralagri.platform.common.web.JsonErrorWriter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
@EnableConfigurationProperties(AuthProperties.class)
@MapperScan(basePackageClasses = AccountMapper.class)
public class AuthConfiguration {
    @Bean
    Clock authClock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    AccountRepository accountRepository(AccountMapper mapper) { return new MyBatisAccountRepository(mapper); }

    @Bean
    JwtService jwtService(AuthProperties properties, Clock clock) { return new JwtService(properties, clock); }

    @Bean
    SessionStore sessionStore(StringRedisTemplate redis, Clock clock) { return new RedisSessionStore(redis, clock); }

    @Bean
    AuthRateLimiter authRateLimiter(StringRedisTemplate redis, AuthProperties properties) {
        return new RedisAuthRateLimiter(redis, properties);
    }

    @Bean
    AuthService authService(AccountRepository accounts, PasswordEncoder passwords, JwtService jwt,
                            SessionStore sessions, AuthProperties properties) {
        return new AuthService(accounts, passwords, jwt, sessions, properties);
    }

    @Bean
    BearerSessionFilter bearerSessionFilter(JwtService jwt, SessionStore sessions, AuthService auth,
                                            JsonErrorWriter errors) {
        return new BearerSessionFilter(jwt, sessions, auth, errors);
    }

    @Bean
    FilterRegistrationBean<BearerSessionFilter> disableServletBearerFilter(BearerSessionFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
