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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * 请求HOst指定值
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: host_route
 *         uri: http://example.org
 *         predicates:
 *         - Host=**.somehost.org
 *
 */
public class HostRoutePredicateFactory extends AbstractRoutePredicateFactory<HostRoutePredicateFactory.Config> {

	/**
	 *  属性，路径匹配器，默认使用 org.springframework.util.AntPathMatcher 。
	 *  通过 #setPathMatcher(PathMatcher) 方法，可以重新设置
	 */
	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public HostRoutePredicateFactory() {
		super(Config.class);
	}

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(PATTERN_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return exchange -> {
			String host = exchange.getRequest().getHeaders().getFirst("Host");
			//匹配
			boolean match = this.pathMatcher.match(config.getPattern(), host);
			if (match) {
				Map<String, String> variables = this.pathMatcher.extractUriTemplateVariables(config.getPattern(), host);
				ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
			}
			return match;
		};
	}

	@Validated
	public static class Config {
		private String pattern;

		public String getPattern() {
			return pattern;
		}

		public Config setPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("pattern", pattern)
					.toString();
		}
	}
}
