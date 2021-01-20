package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * 转发路由网关过滤器。其根据 forward:// 前缀( Scheme )过滤处理，将请求转发到当前网关实例本地接口。
 * spring:
 *   application:
 *       name: juejin-gateway
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: forward_sample
 *         uri: forward:///globalfilters
 *         order: 10000
 *         predicates:
 *         - Path=/globalfilters
 *         filters:
 *         - PrefixPath=/application/gateway
 *
 *
 *  我们假定网关端口为 8080 。
 * 当请求 http://127.0.0.1:8080/globalfilters 接口，Spring Cloud Gateway 处理过程如下 ：
 * 1、匹配到路由 Route (id = forward_sample) 。
 * 2、配置的 PrefixPathGatewayFilterFactory 将请求改写成 http://127.0.0.1:8080/application/gateway/globalfilters 。
 * 3、ForwardRoutingFilter 判断有 forward:// 前缀( Scheme )，过滤处理，将请求转发给 DispatcherHandler 。
 * 4、DispatcherHandler 匹配并转发到当前网关实例本地接口 application/gateway/globalfilters 。
 * 为什么需要配置 PrefixPathGatewayFilterFactory ？需要通过 PrefixPathGatewayFilterFactory 将请求重写路径，以匹配本地 API ，否则 DispatcherHandler 转发会失败。
 *
 *
 */
public class ForwardRoutingFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ForwardRoutingFilter.class);

	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;
	//do not use this dispatcherHandler directly, use getDispatcherHandler() instead.
	private volatile DispatcherHandler dispatcherHandler;

	public ForwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	private DispatcherHandler getDispatcherHandler() {
		if (dispatcherHandler == null) {
			dispatcherHandler = dispatcherHandlerProvider.getIfAvailable();
		}

		return dispatcherHandler;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		//获得request url
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		/**
		 * 判断 ForwardRoutingFilter 是否能够处理该请求，需要满足两个条件 ：
		 * 1、forward:// 前缀( Scheme ) 。
		 * 2、调用 ServerWebExchangeUtils#isAlreadyRouted(ServerWebExchange) 方法，
		 * 判断该请求暂未被其他 Routing 网关处理。
		 */
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}
		//这支该请求已被处理
		setAlreadyRouted(exchange);

		//TODO: translate url?

		if (log.isTraceEnabled()) {
			log.trace("Forwarding to URI: "+requestUrl);
		}
		/**
		 * DispatcherHandler 匹配并转发到当前网关实例本地接口
		 */
		return this.getDispatcherHandler().handle(exchange);
	}
}
