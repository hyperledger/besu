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
        getNextMapLevel(observations, observation.getCategory().getName());
    if (observation.getLabels().isEmpty()) {
      categoryObservations.put(observation.getMetricName(), observation.getValue());
    } else {
      addLabelledObservation(categoryObservations, observation);
    }
  }

  private void addLabelledObservation(
      final Map<String, Object> categoryObservations, final Observation observation) {
    final List<String> labels = observation.getLabels();
    Map<String, Object> values = getNextMapLevel(categoryObservations, observation.getMetricName());
    for (int i = 0; i < labels.size() - 1; i++) {
      values = getNextMapLevel(values, labels.get(i));
    }
    values.put(labels.get(labels.size() - 1), observation.getValue());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getNextMapLevel(
      final Map<String, Object> current, final String name) {
    return (Map<String, Object>)
        current.computeIfAbsent(name, key -> new HashMap<String, Object>());
  }
}
