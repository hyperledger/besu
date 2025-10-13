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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MetricsHttpServiceTest {

  private PrometheusMetricsSystem metricsSystem;
  private MetricsHttpService service;
  private OkHttpClient client;
  private String baseUrl;

  private void initServerAndClient(
      final MetricsConfiguration metricsConfiguration, final boolean start) {
    metricsSystem = (PrometheusMetricsSystem) MetricsSystemFactory.create(metricsConfiguration);
    service = createMetricsHttpService(metricsConfiguration, metricsSystem);
    if (start) {
      service.start().join();
    }

    client = new OkHttpClient();
    baseUrl = urlForSocketAddress("http", service.socketAddress());
  }

  private void initServerAndClient(final boolean start) {
    initServerAndClient(createMetricsConfig(), start);
  }

  private void initServerAndClient() {
    initServerAndClient(createMetricsConfig(), true);
  }

  @AfterEach
  public void stopServer() {
    metricsSystem.shutdown();
    service.stop();
  }

  private MetricsHttpService createMetricsHttpService(
      final MetricsConfiguration config, final PrometheusMetricsSystem metricsSystem) {
    GlobalOpenTelemetry.resetForTest();
    return new MetricsHttpService(config, metricsSystem);
  }

  private static MetricsConfiguration createMetricsConfig() {
    return createMetricsConfigBuilder().build();
  }

  private static MetricsConfiguration.Builder createMetricsConfigBuilder() {
    return MetricsConfiguration.builder().enabled(true).port(0).hostsAllowlist(singletonList("*"));
  }

  @Test
  public void invalidCallToStart() {
    initServerAndClient();
    service
        .start()
        .whenComplete(
            (unused, exception) -> assertThat(exception).isInstanceOf(IllegalStateException.class));
  }

  @Test
  public void http404() throws Exception {
    initServerAndClient();
    try (final Response resp = client.newCall(buildGetRequest("/foo")).execute()) {
      assertThat(resp.code()).isEqualTo(404);
    }
  }

  @Test
  public void handleEmptyRequest() throws Exception {
    initServerAndClient();
    try (final Response resp = client.newCall(buildGetRequest("")).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  @Test
  public void getSocketAddressWhenActive() {
    initServerAndClient();
    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("127.0.0.1").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(socketAddress.getPort() > 0).isTrue();
  }

  @Test
  public void getSocketAddressWhenStoppedIsEmpty() {
    initServerAndClient(false);

    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("0.0.0.0").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(0).isEqualTo(socketAddress.getPort());
    assertThat(new InetSocketAddress("0.0.0.0", 0)).isEqualTo(service.socketAddress());
  }

  @Test
  public void getSocketAddressWhenBindingToAllInterfaces() {
    initServerAndClient(createMetricsConfigBuilder().host("0.0.0.0").build(), true);

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
    initServerAndClient();
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
    initServerAndClient();
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
    initServerAndClient();
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
    initServerAndClient();
    final Request metricsRequest =
        new Request.Builder().addHeader("Accept", "text/xml").url(baseUrl + "/metrics").build();
    try (final Response resp = client.newCall(metricsRequest).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result, it maps to java.util.Properties
      final Properties props = new Properties();
      props.load(resp.body().byteStream());

      // We should have JVM metrics already loaded, verify a simple key.
      assertThat(props).containsKey("jvm_threads_deadlocked");
      assertThat(resp.header("Content-Type")).contains(PrometheusTextFormatWriter.CONTENT_TYPE);
    }
  }

  private Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }
}
