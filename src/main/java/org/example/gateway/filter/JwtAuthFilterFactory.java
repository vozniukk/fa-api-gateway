package org.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.shared.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter для API Gateway
 * Валидирует JWT токены перед маршрутизацией
 */
@Slf4j
@Component
public class JwtAuthFilterFactory extends AbstractGatewayFilterFactory<JwtAuthFilterFactory.Config> {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Проверить Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (!StringUtils.hasText(authHeader)) {
                log.warn("Missing Authorization header");
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            // Извлечь JWT token
            String token;
            if (authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                log.warn("Invalid Authorization header format");
                return onError(exchange, "Invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            // Валидировать JWT
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("Invalid or expired JWT token");
                return onError(exchange, "Invalid or expired JWT token", HttpStatus.UNAUTHORIZED);
            }

            log.debug("JWT token validated successfully");

            // Продолжить выполнение
            return chain.filter(exchange);
        };
    }

    /**
     * Обработать ошибку аутентификации
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().set("Content-Type", "application/json");

        String errorBody = String.format(
                "{\"error\":\"unauthorized\",\"error_description\":\"%s\",\"status\":%d}",
                message,
                httpStatus.value()
        );

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(errorBody.getBytes()))
        );
    }

    public static class Config {
        // Конфигурация фильтра (если нужна)
    }
}

