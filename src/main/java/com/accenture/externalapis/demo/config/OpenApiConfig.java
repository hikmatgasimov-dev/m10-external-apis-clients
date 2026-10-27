package com.accenture.externalapis.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

  @Bean
    public OpenAPI bookCatalogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Book Catalog API")
                .description("Books fetched from an external service, using blocking (RestClient) "
                        + "and non-blocking (WebClient) clients")
                .version("v1"));
    }
}