package com.casestudy.couriertracking.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI courierTrackingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Courier Tracking API")
                .description("Store-entrance detection and total travel distance for courier location streams.")
                .version("v1"));
    }
}
