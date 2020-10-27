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
package org.hyperledger.besu.metrics.prometheus;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.util.NetworkUtility.urlForSocketAddress;

import org.hyperledger.besu.metrics.MetricsSystemFactory;

import java.net.InetSocketAddress;
import java.util.Properties;

import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MetricsHttpServiceTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final Vertx vertx = Vertx.vertx();

  private static MetricsHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;

  @BeforeClass
  public static void initServerAndClient() {
    service = createMetricsHttpService();
    service.start().join();

    // Build an OkHttp client.
    client = new OkHttpClient();
    baseUrl = urlForSocketAddress("http", service.socketAddress());
  }

  private static MetricsHttpService createMetricsHttpService(final MetricsConfiguration config) {
    return new MetricsHttpService(vertx, config, MetricsSystemFactory.create(config));
  }

  private static MetricsHttpService createMetricsHttpService() {
    final MetricsConfiguration metricsConfiguration = createMetricsConfig();
    return new MetricsHttpService(
        vertx, metricsConfiguration, MetricsSystemFactory.create(metricsConfiguration));
  }

  private static MetricsConfiguration createMetricsConfig() {
    return createMetricsConfigBuilder().build();
  }

  private static MetricsConfiguration.Builder createMetricsConfigBuilder() {
    return MetricsConfiguration.builder().enabled(true).port(0).hostsAllowlist(singletonList("*"));
  }

  /** Tears down the HTTP server. */
  @AfterClass
  public static void shutdownServer() {
    service.stop().join();
    vertx.close();
  }

  @Test
  public void invalidCallToStart() {
    service
        .start()
        .whenComplete(
            (unused, exception) -> assertThat(exception).isInstanceOf(IllegalStateException.class));
  }

  @Test
  public void http404() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("/foo")).execute()) {
      assertThat(resp.code()).isEqualTo(404);
    }
  }

  @Test
  public void handleEmptyRequest() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("")).execute()) {
      assertThat(resp.code()).isEqualTo(201);
    }
  }

  @Test
  public void getSocketAddressWhenActive() {
    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("127.0.0.1").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(socketAddress.getPort() > 0).isTrue();
  }

  @Test
  public void getSocketAddressWhenStoppedIsEmpty() {
    final MetricsHttpService service = createMetricsHttpService();

    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("0.0.0.0").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(0).isEqualTo(socketAddress.getPort());
    assertThat(new InetSocketAddress("0.0.0.0", 0)).isEqualTo(service.socketAddress());
  }

  @Test
  public void getSocketAddressWhenBindingToAllInterfaces() {
    final MetricsConfiguration config = createMetricsConfigBuilder().host("0.0.0.0").build();
    final MetricsHttpService service = createMetricsHttpService(config);
    service.start().join();

    try {
      final InetSocketAddress socketAddress = service.socketAddress();
      assertThat("0.0.0.0").isEqualTo(socketAddress.getAddress().getHostAddress());
      assertThat(socketAddress.getPort() > 0).isTrue();
    } finally {
      service.stop().join();
    }
  }

  @Test
  public void metricsArePresent() throws Exception {
    final Request metricsRequest = new Request.Builder().url(baseUrl + "/metrics").build();
    try (final Response resp = client.newCall(metricsRequest).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result, it maps to java.util.Properties
      final Properties props = new Properties();
      props.load(resp.body().byteStream());

      // We should have JVM metrics already loaded, verify a simple key.
      assertThat(props).containsKey("jvm_threads_deadlocked");
    }
  }

  @Test
  public void metricsArePresentWhenFiltered() throws Exception {
    final Request metricsRequest =
        new Request.Builder().url(baseUrl + "/metrics?name[]=jvm_threads_deadlocked").build();
    try (final Response resp = client.newCall(metricsRequest).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result, it maps to java.util.Properties
      final Properties props = new Properties();
      props.load(resp.body().byteStream());

      // We should have JVM metrics already loaded, verify a simple key.
      assertThat(props).containsKey("jvm_threads_deadlocked");
    }
  }

  @Test
  public void metricsAreAbsentWhenFiltered() throws Exception {
    final Request metricsRequest =
        new Request.Builder().url(baseUrl + "/metrics?name[]=does_not_exist").build();
    try (final Response resp = client.newCall(metricsRequest).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result, it maps to java.util.Properties
      final Properties props = new Properties();
      props.load(resp.body().byteStream());

      // We should have JVM metrics already loaded, verify a simple key.
      assertThat(props).isEmpty();
    }
  }

  @Test
  // There is only one available representation so content negotiation should not be used
  public void acceptHeaderIgnored() throws Exception {
    final Request metricsRequest =
        new Request.Builder().addHeader("Accept", "text/xml").url(baseUrl + "/metrics").build();
    try (final Response resp = client.newCall(metricsRequest).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result, it maps to java.util.Properties
      final Properties props = new Properties();
      props.load(resp.body().byteStream());

      // We should have JVM metrics already loaded, verify a simple key.
      assertThat(props).containsKey("jvm_threads_deadlocked");
      assertThat(resp.header("Content-Type")).contains(TextFormat.CONTENT_TYPE_004);
    }
  }

  private Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }
}
