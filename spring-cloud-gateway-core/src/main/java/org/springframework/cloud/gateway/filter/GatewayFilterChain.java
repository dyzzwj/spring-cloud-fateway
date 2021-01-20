package org.springframework.cloud.gateway.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Contract to allow a {@link WebFilter} to delegate to the next in the chain.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GatewayFilterChain {

	/**
	 * Delegate to the next {@code WebFilter} in the chain.
	 * @param exchange the current server exchange
	 * @return {@code Mono<Void>} to indicate when request handling is complete
	 */

	/**
	 * 过滤器链 只支持GatewayFilter调用  GlobalFilter需要使用GatewayFilterAdapter适配
	 */
	Mono<Void> filter(ServerWebExchange exchange);

}
