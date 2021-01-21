/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * 创建 RequestRateLimiterGatewayFilter ( 实际是内部匿名类，为了表述方便，下面继续这么称呼 ) 。
 * RequestRateLimiterGatewayFilter 使用 Redis + Lua 实现分布式限流。
 * 而限流的粒度，例如 URL / 用户 / IP 等，通过 KeyResolver 实现类决定
 *
 * spring:
 *   application:
 *     name: cloud-gateway-eureka
 *   redis:
 *     host: localhost
 *     password:
 *     port: 6379
 *   cloud:
 *     gateway:
 *      discovery:
 *         locator:
 *          enabled: true
 *      routes:
 *      - id: requestratelimiter_route
 *        uri: http://example.org
 *        filters:
 *        - name: RequestRateLimiter
 *          args:
 *            redis-rate-limiter.replenishRate: 10
 *            redis-rate-limiter.burstCapacity: 20
 *            key-resolver: "#{@userKeyResolver}"
 *        predicates:
 *          - Method=GET
 *
 *
 * 说明：
 * 1、filter 名称必须是 RequestRateLimiter
 * 2、redis-rate-limiter.replenishRate：令牌桶每秒填充平均速率
 * 3、redis-rate-limiter.burstCapacity：令牌桶的容量，允许在一秒钟内完成的最大请求数
 * 4、key-resolver：使用 SpEL 按名称引用 bean
 *
 */
@ConfigurationProperties("spring.cloud.gateway.filter.request-rate-limiter")
public class RequestRateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<RequestRateLimiterGatewayFilterFactory.Config> {

	public static final String KEY_RESOLVER_KEY = "keyResolver";
	private static final String EMPTY_KEY = "____EMPTY_KEY__";

	//限流器 RedisRateLimiter
	private final RateLimiter defaultRateLimiter;
	//限流键解析器 PrincipalNameKeyResolver  通过实现 KeyResolver 接口，实现获得不同的请求的限流键，例如URL / 用户 / IP 等。
	private final KeyResolver defaultKeyResolver;

	/** Switch to deny requests if the Key Resolver returns an empty key, defaults to true. */
	private boolean denyEmptyKey = true;

	/** HttpStatus to return when denyEmptyKey is true, defaults to FORBIDDEN. */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	public RequestRateLimiterGatewayFilterFactory(RateLimiter defaultRateLimiter,
												  KeyResolver defaultKeyResolver) {
		super(Config.class);
		this.defaultRateLimiter = defaultRateLimiter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	public RateLimiter getDefaultRateLimiter() {
		return defaultRateLimiter;
	}

	public boolean isDenyEmptyKey() {
		return denyEmptyKey;
	}

	public void setDenyEmptyKey(boolean denyEmptyKey) {
		this.denyEmptyKey = denyEmptyKey;
	}

	public String getEmptyKeyStatusCode() {
		return emptyKeyStatusCode;
	}

	public void setEmptyKeyStatusCode(String emptyKeyStatusCode) {
		this.emptyKeyStatusCode = emptyKeyStatusCode;
	}


	/**
	 * RequestRateLimiterGatewayFilter (匿名内部类)
	 * @param config
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		//限流键解析器 获得请求的限流键，例如URL / 用户 / IP 等  PrincipalNameKeyResolver
		KeyResolver resolver = getOrDefault(config.keyResolver, defaultKeyResolver);
		RateLimiter<Object> limiter = getOrDefault(config.rateLimiter, defaultRateLimiter);
		boolean denyEmpty = getOrDefault(config.denyEmptyKey, this.denyEmptyKey);
		HttpStatusHolder emptyKeyStatus = HttpStatusHolder.parse(getOrDefault(config.emptyKeyStatus, this.emptyKeyStatusCode));

		return (exchange, chain) -> {
			Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
			// resolver.resolve()：获得请求的限流键
			return resolver.resolve(exchange).defaultIfEmpty(EMPTY_KEY).flatMap(key -> {
				if (EMPTY_KEY.equals(key)) {
					//如果限流键为空
					if (denyEmpty) {
						//拒绝限流键为空
						setResponseStatus(exchange, emptyKeyStatus);
						return exchange.getResponse().setComplete();
					}
					//允许访问
					return chain.filter(exchange);
				}
				return limiter.isAllowed(route.getId(), key).flatMap(response -> {

					for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
						exchange.getResponse().getHeaders().add(header.getKey(), header.getValue());
					}

					//是否允许被访问
					if (response.isAllowed()) {
						//允许被访问
						return chain.filter(exchange);
					}
					//被限流 不允许访问
					setResponseStatus(exchange, config.getStatusCode());
					return exchange.getResponse().setComplete();
				});
			});
		};
	}

	private <T> T getOrDefault(T configValue, T defaultValue) {
		return (configValue != null) ? configValue : defaultValue;
	}

	public static class Config {
		private KeyResolver keyResolver;
		private RateLimiter rateLimiter;
		private HttpStatus statusCode = HttpStatus.TOO_MANY_REQUESTS;
		private Boolean denyEmptyKey;
		private String emptyKeyStatus;

		public KeyResolver getKeyResolver() {
			return keyResolver;
		}

		public Config setKeyResolver(KeyResolver keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}
		public RateLimiter getRateLimiter() {
			return rateLimiter;
		}

		public Config setRateLimiter(RateLimiter rateLimiter) {
			this.rateLimiter = rateLimiter;
			return this;
		}

		public HttpStatus getStatusCode() {
			return statusCode;
		}

		public Config setStatusCode(HttpStatus statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public Boolean getDenyEmptyKey() {
			return denyEmptyKey;
		}

		public Config setDenyEmptyKey(Boolean denyEmptyKey) {
			this.denyEmptyKey = denyEmptyKey;
			return this;
		}

		public String getEmptyKeyStatus() {
			return emptyKeyStatus;
		}

		public Config setEmptyKeyStatus(String emptyKeyStatus) {
			this.emptyKeyStatus = emptyKeyStatus;
			return this;
		}
	}

}
