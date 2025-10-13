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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugMetrics implements JsonRpcMethod {

  private final ObservableMetricsSystem metricsSystem;

  public DebugMetrics(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_METRICS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Map<String, Object> observations = new HashMap<>();
    metricsSystem
        .streamObservations()
        .forEach(observation -> addObservation(observations, observation));
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), observations);
  }

  private void addObservation(
      final Map<String, Object> observations, final Observation observation) {
    final Map<String, Object> categoryObservations =
        getNextMapLevel(observations, observation.category().getName());
    if (observation.labels().isEmpty()) {
      categoryObservations.put(observation.metricName(), observation.value());
    } else {
      addLabelledObservation(categoryObservations, observation);
    }
  }

  private void addLabelledObservation(
      final Map<String, Object> categoryObservations, final Observation observation) {
    final List<String> labels = observation.labels();
    Map<String, Object> values = getNextMapLevel(categoryObservations, observation.metricName());
    for (int i = 0; i < labels.size() - 1; i++) {
      values = getNextMapLevel(values, labels.get(i));
    }
    values.put(labels.get(labels.size() - 1), observation.value());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getNextMapLevel(
      final Map<String, Object> current, final String name) {
    // Use compute to either return the existing map or create a new one
    return (Map<String, Object>)
        current.compute(
            name,
            (k, v) -> {
              if (v instanceof Map) {
                // If the value is already a Map, return it as is
                return v;
              } else {
                // If the value is not a Map, create a new Map
                Map<String, Object> newMap = new HashMap<>();
                if (v != null) {
                  // If v is not null and not a Map, we store it as a leaf value
                  // If the original value was not null, store it under the "value" key
                  // This handles cases where a metric value (e.g., Double) was previously stored
                  // directly
                  newMap.put("value", v);
                }
                return newMap;
              }
            });
  }
}
