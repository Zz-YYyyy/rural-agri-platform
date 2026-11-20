package com.ruralagri.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI runtimeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("乡村特色农产品平台：已实现接口")
                .version("0.1.0-bootstrap")
                .description("当前只有工程基础与运维健康检查，业务接口尚未实现。"
                        + "完整设计契约在仓库 docs/api/接口契约.openapi.json；"
                        + "运行时页面不能代替设计文档或表示全部业务已经可用。"));
    }
}
