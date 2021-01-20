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

package org.springframework.cloud.gateway.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

import reactor.core.publisher.Mono;

/**
 * Contract for interception-style, chained processing of Web requests that may
 * be used to implement cross-cutting, application-agnostic requirements such
 * as security, timeouts, and others.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */

/**
 * GlobalFilter会作用在所有的Route上
 */
public interface GlobalFilter {

	/**
	 * Process the Web request and (optionally) delegate to the next
	 * {@code WebFilter} through the given {@link GatewayFilterChain}.
	 * @param exchange the current server exchange
	 * @param chain provides a way to delegate to the next filter
	 * @return {@code Mono<Void>} to indicate when request processing is complete
	 */
	/**
	 *  GatewayFilteChain 只支持使用 GatewayFilter 过滤请求，
	 *  所以在FilteringWebHandler#loadFilters中 将 GlobalFilter 委托成 GatewayFilterAdapter
	 */
	Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);

	/**
	 * 目前gateway内置的GlobalFilter	顺序（值越小 顺序越靠前）
	 *
	 * NettyWriteResponseFilter	-1
	 * WebClientWriteResponseFilter	-1
	 * RouteToRequestUrlFilter	10000
	 * LoadBalancerClientFilter	10100
	 * ForwardRoutingFilter	Integer.MAX_VALUE
	 * NettyRoutingFilter	Integer.MAX_VALUE
	 * WebClientHttpRoutingFilter	Integer.MAX_VALUE
	 * WebsocketRoutingFilter	Integer.MAX_VALUE
	 */








}
