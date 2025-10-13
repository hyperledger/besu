/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.tests.acceptance.plugins.TestMetricsPlugin.TestMetricCategory.TEST_METRIC_CATEGORY;

import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricsPluginTest extends AcceptanceTestBase {
  private BesuNode node;
  private MetricsConfiguration metricsConfiguration;

  @BeforeEach
  public void setUp() throws Exception {
    metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(true)
            .port(0)
            .metricCategories(Set.of(TEST_METRIC_CATEGORY))
            .build();
    node =
        besu.create(
            new BesuNodeConfigurationBuilder()
                .name("node1")
                .plugins(List.of("testPlugins"))
                .metricsConfiguration(metricsConfiguration)
                .build());

    cluster.start(node);
  }

  @Test
  public void metricCategoryAdded() throws IOException, InterruptedException {
    final var httpClient = HttpClient.newHttpClient();
    final var req = HttpRequest.newBuilder(URI.create(node.metricsHttpUrl().get())).build();
    final var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofLines());
    assertThat(resp.statusCode()).isEqualTo(200);
    final var foundMetric =
        resp.body()
            .filter(
                line -> line.startsWith(TEST_METRIC_CATEGORY.getApplicationPrefix().orElseThrow()))
            .findFirst()
            .orElseThrow();
    assertThat(foundMetric).endsWith("1.0");
  }
}
