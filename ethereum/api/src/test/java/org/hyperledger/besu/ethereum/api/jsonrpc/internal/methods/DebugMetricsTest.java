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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.BesuMetricCategory.BLOCKCHAIN;
import static org.hyperledger.besu.metrics.BesuMetricCategory.PEERS;
import static org.hyperledger.besu.metrics.BesuMetricCategory.RPC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

public class DebugMetricsTest {

  private static final JsonRpcRequestContext REQUEST =
      new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_metrics", new Object[0]));
  private final ObservableMetricsSystem metricsSystem = mock(ObservableMetricsSystem.class);

  private final DebugMetrics method = new DebugMetrics(metricsSystem);

  @Test
  public void shouldHaveCorrectName() {
    assertThat(method.getName()).isEqualTo("debug_metrics");
  }

  @Test
  public void shouldReportUnlabelledObservationsByCategory() {
    when(metricsSystem.streamObservations())
        .thenReturn(
            Stream.of(
                new Observation(PEERS, "peer1", "peer1Value", Collections.emptyList()),
                new Observation(PEERS, "peer2", "peer2Value", Collections.emptyList()),
                new Observation(RPC, "rpc1", "rpc1Value", Collections.emptyList())));

    assertResponse(
        ImmutableMap.of(
            PEERS.getName(),
            ImmutableMap.<String, Object>of("peer1", "peer1Value", "peer2", "peer2Value"),
            RPC.getName(),
            ImmutableMap.<String, Object>of("rpc1", "rpc1Value")));
  }

  @Test
  public void shouldNestObservationsByLabel() {
    when(metricsSystem.streamObservations())
        .thenReturn(
            Stream.of(
                new Observation(PEERS, "peer1", "value1", asList("label1A", "label2A")),
                new Observation(PEERS, "peer1", "value2", asList("label1A", "label2B")),
                new Observation(PEERS, "peer1", "value3", asList("label1B", "label2B"))));

    assertResponse(
        ImmutableMap.of(
            PEERS.getName(),
            ImmutableMap.<String, Object>of(
                "peer1",
                ImmutableMap.of(
                    "label1A",
                    ImmutableMap.of("label2A", "value1", "label2B", "value2"),
                    "label1B",
                    ImmutableMap.of("label2B", "value3")))));
  }

  @Test
  public void shouldHandleDoubleValuesInNestedStructureWithoutClassCastException() {
    // Tests fix for issue# 7383: debug_metrics method error
    when(metricsSystem.streamObservations())
        .thenReturn(
            Stream.of(
                // This creates a double value for "a"
                new Observation(BLOCKCHAIN, "nested_metric", 1.0, List.of("a")),
                // This attempts to create a nested structure under "a", which was previously a
                // double
                new Observation(BLOCKCHAIN, "nested_metric", 2.0, asList("a", "b")),
                // This adds another level of nesting
                new Observation(BLOCKCHAIN, "nested_metric", 3.0, asList("a", "b", "c"))));

    assertResponse(
        ImmutableMap.of(
            BLOCKCHAIN.getName(),
            ImmutableMap.of(
                "nested_metric",
                ImmutableMap.of(
                    "a",
                    ImmutableMap.of(
                        "value",
                        1.0,
                        "b",
                        ImmutableMap.of(
                            "value", 2.0,
                            "c", 3.0))))));
  }

  private void assertResponse(final ImmutableMap<String, Object> expectedResponse) {
    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(REQUEST);
    assertThat(response.getResult()).isEqualTo(expectedResponse);
  }
}
