package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration

public class Webconfig {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        Resource requestedResource = location.createRelative(resourcePath);
                        return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}