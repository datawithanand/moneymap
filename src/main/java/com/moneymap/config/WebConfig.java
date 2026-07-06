package com.moneymap.config;

import com.moneymap.web.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String dataDir;

    public WebConfig(AuthInterceptor authInterceptor, @Value("${moneymap.data-dir}") String dataDir) {
        this.authInterceptor = authInterceptor;
        this.dataDir = dataDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Profile photos live under DATA_DIR/uploads (Section 01 §5.1); served behind the auth interceptor.
        String uploads = Paths.get(dataDir, "uploads").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploads);
    }
}
