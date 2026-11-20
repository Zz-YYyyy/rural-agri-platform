package com.ruralagri.platform.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

import com.ruralagri.platform.common.web.BusinessException;
import com.ruralagri.platform.common.web.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerSessionFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SessionStore sessions;
    private final AuthService auth;
    private final JsonErrorWriter errors;

    public BearerSessionFilter(JwtService jwt, SessionStore sessions, AuthService auth, JsonErrorWriter errors) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.auth = auth;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/") || "POST".equals(request.getMethod())
                && ("/api/v1/auth/register".equals(path) || "/api/v1/auth/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7
                    || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) {
                throw AuthErrors.unauthenticated();
            }
            var verified = jwt.verify(header.substring(7));
            boolean logout = "POST".equals(request.getMethod())
                    && "/api/v1/auth/logout".equals(
                            request.getRequestURI().substring(request.getContextPath().length()));
            Optional<String> storedId = sessions.findAccountId(verified.jti());
            if (storedId.isPresent() && !storedId.get().equals(Long.toString(verified.accountId()))
                    || storedId.isEmpty() && !logout) {
                throw AuthErrors.unauthenticated();
            }
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            AccountView accountView = null;
            if (!logout) {
                StoredAccount account = auth.currentAccount(verified.accountId());
                accountView = auth.toView(account);
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                if (account.admin()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }
                if (account.farmerId() != null && "ACTIVE".equals(account.farmerStatus())) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_FARMER"));
                }
            }
            // Logout needs only proof of ownership of this jti, including after account disablement.
            // It grants no business roles, and the missing-session exception is exact-path/method only.
            var principal = new AccountPrincipal(verified.accountId(), verified.jti(), accountView);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            errors.write(request, response, exception.getStatus().value(), exception.getCode(), exception.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }
}
