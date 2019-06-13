/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.health;

import com.google.common.collect.ImmutableMap;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class HealthService {

  public static final String LIVENESS_PATH = "/liveness";
  public static final String READINESS_PATH = "/readiness";

  private static final int HEALTHY_STATUS_CODE = HttpResponseStatus.OK.code();
  private static final int NON_HEALTHY_STATUS_CODE = HttpResponseStatus.SERVICE_UNAVAILABLE.code();

  private final HealthCheck healthCheck;

  public HealthService(final HealthCheck healthCheck) {
    this.healthCheck = healthCheck;
  }

  public void handleRequest(final RoutingContext routingContext) {
    final HealthCheckResponse healthCheckResponse = buildResponse(isHealthy());
    routingContext
        .response()
        .setStatusCode(healthCheckResponse.statusCode)
        .end(healthCheckResponse.responseBody);
  }

  public boolean isHealthy() {
    return healthCheck.isHealthy();
  }

  private HealthCheckResponse buildResponse(final boolean healthy) {
    return new HealthCheckResponse(
        healthy ? HEALTHY_STATUS_CODE : NON_HEALTHY_STATUS_CODE,
        new JsonObject(ImmutableMap.of("status", healthy ? "UP" : "DOWN")).encodePrettily());
  }

  @FunctionalInterface
  public interface HealthCheck {
    boolean isHealthy();
  }

  private static class HealthCheckResponse {
    private int statusCode;
    private String responseBody;

    private HealthCheckResponse(final int statusCode, final String responseBody) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }
  }
}
