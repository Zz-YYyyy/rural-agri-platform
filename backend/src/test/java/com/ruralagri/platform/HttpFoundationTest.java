package com.ruralagri.platform;

import java.util.Map;

import com.ruralagri.platform.common.web.ApiResponse;
import com.ruralagri.platform.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({HttpFoundationTest.TestEndpoints.class, HttpFoundationTest.TestSecurity.class})
class HttpFoundationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void bootstrapHealthDoesNotRequireInfrastructureOrExposeDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().string(TraceIdFilter.HEADER, matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void placeholderBearerCannotAccessBusinessRoutes() throws Exception {
        var result = mvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer not-a-real-token")
                        .header(TraceIdFilter.HEADER, "client-cannot-set-server-trace")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(result.getResponse().getHeader(TraceIdFilter.HEADER))
                .doesNotContain("client-cannot-set-server-trace");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void evenAuthenticatedIdentityCannotBypassUnimplementedAuthorization() throws Exception {
        mvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validationAndUnknownJsonFieldsUseSafeErrorEnvelope() throws Exception {
        mvc.perform(post("/__test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        mvc.perform(post("/__test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"柑橘\",\"isAdmin\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalMessages() throws Exception {
        mvc.perform(get("/__test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal-db-details"))));
    }

    @Test
    void runtimeApiDocsDoNotPretendBusinessContractsAreImplemented() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.paths['/api/v1/me/orders']").doesNotExist());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurity {
        @Bean
        @Order(-1)
        SecurityFilterChain testEndpointsSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__test/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
        }
    }

    // Test-only routes never appear in the production artifact.
    @RestController
    static class TestEndpoints {
        @PostMapping("/__test/validation")
        ApiResponse<Map<String, String>> validation(@Valid @RequestBody NamedRequest body,
                                                    HttpServletRequest request) {
            return ApiResponse.success(Map.of("name", body.name()), TraceIdFilter.current(request));
        }

        @GetMapping("/__test/failure")
        void failure() {
            throw new IllegalStateException("internal-db-details");
        }
    }

    record NamedRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
