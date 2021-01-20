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

package org.springframework.cloud.gateway.route;

import reactor.core.publisher.Flux;

/**
 * RouteLocator 可以直接自定义路由( org.springframework.cloud.gateway.route.Route ) ，
 * 也可以通过 RouteDefinitionRouteLocator 获取 RouteDefinition ，并转换成 Route 。
 * RoutePredicateHandlerMapping 使用 RouteLocator 获得 Route 信息。
 * 获取Route
 */
public interface RouteLocator {

	Flux<Route> getRoutes();
}
