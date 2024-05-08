/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.health;

import static java.util.Collections.singletonMap;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** The type Health service. */
public final class HealthService {

  /** The constant ALWAYS_HEALTHY. */
  public static final HealthService ALWAYS_HEALTHY = new HealthService(params -> true);

  /** The constant LIVENESS_PATH. */
  public static final String LIVENESS_PATH = "/liveness";

  /** The constant READINESS_PATH. */
  public static final String READINESS_PATH = "/readiness";

  private static final int HEALTHY_STATUS_CODE = HttpResponseStatus.OK.code();
  private static final int UNHEALTHY_STATUS_CODE = HttpResponseStatus.SERVICE_UNAVAILABLE.code();
  private static final String HEALTHY_STATUS_TEXT = "UP";
  private static final String UNHEALTHY_STATUS_TEXT = "DOWN";

  private final HealthCheck healthCheck;

  /**
   * Instantiates a new Health service.
   *
   * @param healthCheck the health check
   */
  public HealthService(final HealthCheck healthCheck) {
    this.healthCheck = healthCheck;
  }

  /**
   * Handle request.
   *
   * @param routingContext the routing context
   */
  public void handleRequest(final RoutingContext routingContext) {
    final int statusCode;
    final String statusText;
    if (healthCheck.isHealthy(name -> routingContext.queryParams().get(name))) {
      statusCode = HEALTHY_STATUS_CODE;
      statusText = HEALTHY_STATUS_TEXT;
    } else {
      statusCode = UNHEALTHY_STATUS_CODE;
      statusText = UNHEALTHY_STATUS_TEXT;
    }
    final HttpServerResponse response = routingContext.response();
    if (!response.closed()) {
      response
          .setStatusCode(statusCode)
          .end(new JsonObject(singletonMap("status", statusText)).encodePrettily());
    }
  }

  /** The interface Health check. */
  @FunctionalInterface
  public interface HealthCheck {
    /**
     * Is healthy boolean.
     *
     * @param paramSource the param source
     * @return the boolean
     */
    boolean isHealthy(ParamSource paramSource);
  }

  /** The interface Param source. */
  @FunctionalInterface
  public interface ParamSource {
    /**
     * Gets param.
     *
     * @param name the name
     * @return the param
     */
    String getParam(String name);
  }
}
