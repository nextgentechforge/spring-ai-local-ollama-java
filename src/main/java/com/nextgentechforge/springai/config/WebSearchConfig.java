package com.nextgentechforge.springai.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebSearchConfig {
    @Bean
    public RestClient webSearchClient(@Value("${app.web-search.base-url}") URI baseUrl,
                               @Value("${app.web-search.timeout:10s}") Duration timeout) {
        if (baseUrl.getHost() == null || !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException("Web search base URL must be an HTTP(S) service URL without credentials or query");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("Web search timeout must be positive and at most 60 seconds");
        }
        var http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(factory).build();
    }
}
