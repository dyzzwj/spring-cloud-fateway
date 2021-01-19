/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * yaml配置解析
 * spring:
 *   cloud:
 *     gateway: # ①
 *       routes: # ②
 *       - id: cookie_route # ③
 *         uri: http://example.org # ④
 *         predicates: # ⑤
 *         - Cookie=chocolate, ch.p # ⑥
 *         filters: # ⑦
 *         - AddRequestHeader=X-Request-Foo, Bar # ⑧
 *
 * ① "spring.cloud.gateway" 为固定前缀。
 *
 * ② 定义路由信息列表，即可定义多个路由。
 *
 * ③ 声明了一个 id 为 "cookie_route" 的路由。
 *
 * ④ 定义了路由的目的地 uri，即请求转发的目的地。
 *
 * ⑤ 声明 predicates，即请求满足相应的条件才能匹配成功。
 *
 * ⑥ 定义了一个 Predicate，当名称为 chocolate 的 Cookie 的值匹配ch.p时 Predicate 才能够匹配，它由 CookieRoutePredicateFactory 来生产。
 *
 * ⑦ 声明 filters，即路由转发前后处理的过滤器。
 *
 * ⑧ 定义了一个 Filter，所有的请求转发至下游服务时会添加请求头 X-Request-Foo:Bar ，由AddRequestHeaderGatewayFilterFactory 来生产。
 */

@ConfigurationProperties("spring.cloud.gateway")
@Validated
public class GatewayProperties {

	private final Log logger = LogFactory.getLog(getClass());
	/**
	 * List of Routes
	 */
	@NotNull
	@Valid
	private List<RouteDefinition> routes = new ArrayList<>();

	/**
	 * List of filter definitions that are applied to every route.
	 */
	private List<FilterDefinition> defaultFilters = new ArrayList<>();

	private List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			MediaType.APPLICATION_STREAM_JSON);

	public List<RouteDefinition> getRoutes() {
		return routes;
	}


	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
		if (routes != null && routes.size() > 0 && logger.isDebugEnabled()) {
			logger.debug("Routes supplied from Gateway Properties: "+routes);
		}
	}

	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}

	public List<MediaType> getStreamingMediaTypes() {
		return streamingMediaTypes;
	}

	public void setStreamingMediaTypes(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	@Override
	public String toString() {
		return "GatewayProperties{" +
				"routes=" + routes +
				", defaultFilters=" + defaultFilters +
				", streamingMediaTypes=" + streamingMediaTypes +
				'}';
	}
}
