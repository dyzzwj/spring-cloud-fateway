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

import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * 根据配置的正则表达式 regexp ，使用配置的 replacement 重写请求 Path
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: rewritepath_route
 *         uri: http://example.org
 *         predicates:
 *         - Path=/foo/**
 *         filters:
 *         - RewritePath=/foo/(?<segment>.*), /$\{segment}
 *
 */
public class RewritePathGatewayFilterFactory extends AbstractGatewayFilterFactory<RewritePathGatewayFilterFactory.Config> {

	public static final String REGEXP_KEY = "regexp";
	public static final String REPLACEMENT_KEY = "replacement";

	public RewritePathGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(REGEXP_KEY, REPLACEMENT_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		// `$\` 用于替代 `$` ，避免和 YAML 语法冲突。
		String replacement = config.replacement.replace("$\\", "$");
		return (exchange, chain) -> {
			ServerHttpRequest req = exchange.getRequest();
			//添加原始请求url到到 GATEWAY_ORIGINAL_REQUEST_URL_ATTR
			addOriginalRequestUrl(exchange, req.getURI());
			String path = req.getURI().getRawPath();
			//重写path
			String newPath = path.replaceAll(config.regexp, replacement);

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			// 添加 新的请求URI 到 GATEWAY_REQUEST_URL_ATTR
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());
			// 创建新的 ServerWebExchange ，提交过滤器链继续过滤
			return chain.filter(exchange.mutate().request(request).build());
		};
	}

	public static class Config {
		private String regexp;
		private String replacement;

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

		public String getReplacement() {
			return replacement;
		}

		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}
	}
}
