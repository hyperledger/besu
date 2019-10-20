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
package org.hyperledger.besu.tests.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;

public class MetricsAcceptanceTest extends AcceptanceTestBase {

  private BesuNode metricsNode;
  private OkHttpClient client;

  @Before
  public void setUp() throws Exception {
    metricsNode =
        besu.create(
            new BesuNodeConfigurationBuilder().name("metrics-node").metricsEnabled().build());
    cluster.start(metricsNode);
    client = new OkHttpClient();
  }

  @Test
  public void metricsReporting() throws IOException {
    assertThat(metricsNode.metricsHttpUrl()).isPresent();

    final Call metricsRequest =
        client.newCall(new Request.Builder().url(metricsNode.metricsHttpUrl().get()).build());
    final Response response = metricsRequest.execute();

    assertThat(response.body().string()).contains("# TYPE besu_peers_connected_total counter");
  }
}
