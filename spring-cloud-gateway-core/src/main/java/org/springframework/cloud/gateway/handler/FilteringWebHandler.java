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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link GatewayFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 */
public class FilteringWebHandler implements WebHandler {
	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);

	/**
	 * 全局过滤器 用户可以实现GatewayFilter自定义全局过滤器 加入到容器中
	 */
	private final List<GatewayFilter> globalFilters;

	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this.globalFilters = loadFilters(globalFilters);
	}

	/**
	 * 在 GatewayFilterChain 只支持使用 GatewayFilter 过滤请求， 所以将 GlobalFilter 委托成 GatewayFilterAdapter
	 * @param filters
	 * @return
	 */
	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream()
				.map(filter -> {
					GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
					/**
					 *  GlobalFilter 子类实现了 org.springframework.core.Ordered 接口，在委托一层 OrderedGatewayFilter 。
					 *  这样 AnnotationAwareOrderComparator#sort(List) 方法好排序
					 */
					if (filter instanceof Ordered) {
						int order = ((Ordered) filter).getOrder();
						return new OrderedGatewayFilter(gatewayFilter, order);
					}
					/**
					 * 当 GlobalFilter 子类没有实现了 org.springframework.core.Ordered 接口，
					 * 在 AnnotationAwareOrderComparator#sort(List) 排序时，顺序值为 Integer.MAX_VALUE
					 * 目前 GlobalFilter 都实现了 org.springframework.core.Ordered 接口
					 */
					return gatewayFilter;
				}).collect(Collectors.toList());
	}

    /* TODO: relocate @EventListener(RefreshRoutesEvent.class)
    void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }*/

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		//获取在RoutePredicateHandlerMapping.getHandlerInternalRoute中设置的Route
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		//获得GatewayFilter
		List<GatewayFilter> gatewayFilters = route.getFilters();

		//将全局过滤器添加到过滤器集合中
		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(gatewayFilters);
		//TODO: needed or cached?
		//排序
		AnnotationAwareOrderComparator.sort(combined);

		if (logger.isDebugEnabled()) {
			logger.debug("Sorted gatewayFilterFactories: "+ combined);
		}
		/**
		 * 使用GatewayFilter数组创建过滤器链
		 */
		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;
		private final List<GatewayFilter> filters;

		public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					//循环filter 调用filter()方法
					GatewayFilter filter = filters.get(this.index);
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
					return filter.filter(exchange, chain);
				} else {
					return Mono.empty(); // complete
				}
			});
		}
	}

	/**
	 *  网关过滤器适配器
	 *  在 GatewayFilterChain 使用 GatewayFilter 过滤请求，
	 *  所以通过 GatewayFilterAdapter 将 GlobalFilter 适配成 GatewayFilter
	 */
	private static class GatewayFilterAdapter implements GatewayFilter {

		//GlobalFilter
		private final GlobalFilter delegate;

		public GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}
	}

}
