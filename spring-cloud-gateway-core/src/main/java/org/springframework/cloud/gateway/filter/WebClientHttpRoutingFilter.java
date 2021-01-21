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

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

import reactor.core.publisher.Mono;

/**
 * Http 路由网关过滤器。其根据 http:// 或 https:// 前缀( Scheme )过滤处理，
 * 使用基于 org.springframework.cloud.gateway.filter.WebClient 实现的 HttpClient 请求后端 Http 服务
 */

/**
 * 目前 WebClientHttpRoutingFilter / WebClientWriteResponseFilter 处于实验阶段，建议等正式发布在使用。
 *
 * OK，下面我们来看看怎么配置环境。
 *
 * 第一步，在 NettyConfiguration 注释掉 #routingFilter(...) 和 #nettyWriteResponseFilter() 两个 Bean 方法。
 *
 * 第二步，在 GatewayAutoConfiguration 打开 #webClientHttpRoutingFilter() 和 #webClientWriteResponseFilter() 两个 Bean 方法。
 *
 * 第三步，配置完成，启动 Spring Cloud Gateway 。
 */
public class WebClientHttpRoutingFilter implements GlobalFilter, Ordered {

	/**
	 * 默认情况下，使用 org.springframework.web.reactive.function.client.DefaultWebClient 实现类。
	 * 通过该属性 请求后端服务
	 */
	private final WebClient webClient;

	public WebClientHttpRoutingFilter(WebClient webClient) {
		this.webClient = webClient;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {


		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		/** 判断是否能够处理：
		 * 1、request的schema前缀 http:// 或 https://
		 * 2、该请求暂未被其他Routing网关处理
		 */
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

		HttpMethod method = request.getMethod();

		RequestBodySpec bodySpec = this.webClient.method(method)
				.uri(requestUrl)
				.headers(httpHeaders -> {
					httpHeaders.addAll(request.getHeaders());
					//TODO: can this support preserviceHostHeader?
					httpHeaders.remove(HttpHeaders.HOST);
				});

		RequestHeadersSpec<?> headersSpec;
		if (requiresBody(method)) {
			headersSpec = bodySpec.body(BodyInserters.fromDataBuffers(request.getBody()));
		} else {
			headersSpec = bodySpec;
		}

		return headersSpec.exchange()
				// .log("webClient route")
				.flatMap(res -> {
					ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().putAll(res.headers().asHttpHeaders());
					response.setStatusCode(res.statusCode());
					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					return chain.filter(exchange);
				});
	}

	private boolean requiresBody(HttpMethod method) {
		switch (method) {
			case PUT:
			case POST:
			case PATCH:
				return true;
			default:
				return false;
		}
	}
}
