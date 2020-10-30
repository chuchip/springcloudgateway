package com.profesorp.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient.UriConfiguration;

@SpringBootApplication
@EnableEurekaClient
@RestController
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
	@Bean
	public RouteLocator myRoutes(RouteLocatorBuilder builder) {
		return builder.routes()
			.route(p -> p
				.path("/custom/**")
				.uri("http://localhost:8000"))
			.route(p -> p
				.path("/fallo")
				.filters(f -> f
					.hystrix(config -> config
						.setName("mycmd")
						.setFallbackUri("forward:/fallback")))
				.uri("http://localhost:999"))
			.build();
	}
	@RequestMapping("/fallback")
	public Mono<String> fallback() {
		return Mono.just("Alfo fue mal. Respondido de fallback");
	}
	@Bean
	public GlobalFilter customFilter() {
	    return new CustomGlobalFilter();
	}
	
}
class CustomGlobalFilter implements GlobalFilter, Ordered {
	Logger log = LoggerFactory.getLogger(this.getClass());
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("custom global filter. "+exchange.getRequest().getPath().toString());
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
