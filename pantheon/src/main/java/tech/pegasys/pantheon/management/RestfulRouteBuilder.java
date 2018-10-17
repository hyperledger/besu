/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.management;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

class RestfulRouteBuilder {

  private RestfulRouteBuilder() {}

  static RestfulRoute restfulRoute(final Router router, final String path) {
    return new RestfulRoute(router, path);
  }

  static class RestfulRoute {
    private final Router router;
    private final String path;
    private final Map<HttpMethod, Consumer<RestfulMethodRoute>> methods;

    private RestfulRoute(final Router router, final String path) {
      this.router = router;
      this.path = path;
      this.methods = Collections.emptyMap();
    }

    private RestfulRoute(
        final Router router,
        final String path,
        final Map<HttpMethod, Consumer<RestfulMethodRoute>> methods) {
      this.router = router;
      this.path = path;
      this.methods = methods;
    }

    RestfulRoute method(final HttpMethod method, final Consumer<RestfulMethodRoute> routeBuilder) {
      if (method == HttpMethod.HEAD) {
        throw new IllegalArgumentException("HEAD method should not be handled explicitly");
      }
      if (method == HttpMethod.OPTIONS) {
        throw new IllegalArgumentException("OPTIONS method should not be handled explicitly");
      }

      final Map<HttpMethod, Consumer<RestfulMethodRoute>> updatedMethods =
          new HashMap<>(this.methods);
      updatedMethods.put(method, routeBuilder);
      return new RestfulRoute(router, path, updatedMethods);
    }

    void build() {
      methods.forEach(
          (method, routeBuilder) -> {
            Route route = router.route(path);
            route = route.method(method);
            if (method == HttpMethod.GET) {
              route = route.method(HttpMethod.HEAD);
            }
            routeBuilder.accept(new RestfulMethodRoute(route));
          });
      createUnmatchedContentTypeRoute();
      createOptionsRoute();
      createUnmatchedMethodRoute();
    }

    private void createUnmatchedContentTypeRoute() {
      final Route route = router.route(path);
      methods.keySet().forEach(route::method);
      route.handler(
          routingContext ->
              routingContext
                  .response()
                  .setStatusCode(HttpResponseStatus.NOT_ACCEPTABLE.code())
                  .end());
    }

    private void createOptionsRoute() {
      final Set<HttpMethod> visibleMethods = new HashSet<>(this.methods.keySet());
      visibleMethods.add(HttpMethod.OPTIONS);
      if (visibleMethods.contains(HttpMethod.GET)) {
        visibleMethods.add(HttpMethod.HEAD);
      }

      final String methodsStrings =
          String.join(
              ",",
              visibleMethods.stream().map(HttpMethod::name).sorted().collect(Collectors.toList()));
      router
          .route(path)
          .method(HttpMethod.OPTIONS)
          .handler(
              routingContext ->
                  routingContext
                      .response()
                      .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                      .putHeader(HttpHeaderNames.ALLOW.toString(), methodsStrings)
                      .end());
    }

    private void createUnmatchedMethodRoute() {
      router
          .route(path)
          .handler(
              routingContext ->
                  routingContext
                      .response()
                      .setStatusCode(HttpResponseStatus.METHOD_NOT_ALLOWED.code())
                      .end());
    }
  }

  static class RestfulMethodRoute {
    private final Route route;

    RestfulMethodRoute(final Route route) {
      this.route = route;
    }

    RestfulMethodRoute produces(final String... contentTypes) {
      for (final String contentType : contentTypes) {
        route.produces(contentType);
      }
      return this;
    }

    void handler(final Handler<RoutingContext> handler) {
      route.handler(handler);
    }
  }
}
