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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.nat.NatService;

import java.util.HashMap;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonRpcHttpServiceCorsTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private final Vertx vertx = Vertx.vertx();
  private final OkHttpClient client = new OkHttpClient();
  private JsonRpcHttpService jsonRpcHttpService;

  @Before
  public void before() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
  }

  @After
  public void after() {
    jsonRpcHttpService.stop().join();
  }

  @Test
  public void requestWithNonAcceptedOriginShouldFail() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAcceptedOriginShouldSucceed() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://foo.io")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithOneOfMultipleAcceptedOriginsShouldSucceed() throws Exception {
    jsonRpcHttpService =
        createJsonRpcHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoneOfMultipleAcceptedOriginsShouldFail() throws Exception {
    jsonRpcHttpService =
        createJsonRpcHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://hel.lo")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenNoCorsConfigSet() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains();

    final Request request = new Request.Builder().url(jsonRpcHttpService.url()).build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenCorsIsSet() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request = new Request.Builder().url(jsonRpcHttpService.url()).build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAnyOriginShouldNotSucceedWhenCorsIsEmpty() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAnyOriginShouldSucceedWhenCorsIsStart() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("*");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestFromBrowserExtensionShouldSucceedWhenCorsIsStar() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("*");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .header("Origin", "moz-extension://802123e4-a916-2d4e-bebf-384b0e2e86dd")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAccessControlRequestMethodShouldReturnAllowedHeaders() throws Exception {
    jsonRpcHttpService = createJsonRpcHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(jsonRpcHttpService.url())
            .method("OPTIONS", null)
            .header("Access-Control-Request-Method", "OPTIONS")
            .header("Origin", "http://foo.io")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.header("Access-Control-Allow-Headers")).contains("*", "content-type");
    }
  }

  private JsonRpcHttpService createJsonRpcHttpServiceWithAllowedDomains(
      final String... corsAllowedDomains) throws Exception {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    if (corsAllowedDomains != null) {
      config.setCorsAllowedDomains(Lists.newArrayList(corsAllowedDomains));
    }
    final NatService natService = new NatService(Optional.empty());

    final JsonRpcHttpService jsonRpcHttpService =
        new JsonRpcHttpService(
            vertx,
            folder.newFolder().toPath(),
            config,
            new NoOpMetricsSystem(),
            natService,
            new HashMap<>(),
            HealthService.ALWAYS_HEALTHY,
            HealthService.ALWAYS_HEALTHY);
    jsonRpcHttpService.start().join();

    return jsonRpcHttpService;
  }
}
