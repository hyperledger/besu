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
package org.hyperledger.besu.plugins.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.pluginadapter.NetworkEndpointServiceImpl;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.rpc.RpcConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.NetworkEndpointService;
import org.hyperledger.besu.plugin.services.NetworkEndpointService.NetworkEndpoint;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Drives the GraphQL plugin only through its {@link BesuPlugin} lifecycle, the way Besu does: the
 * plugin registers its options and endpoint, Besu parses the command line, adds the real plugin
 * service implementations over the hive test chain and starts the plugin, which then serves every
 * spec fixture on the port it reports through its network endpoint. Each fixture runs on a Bonsai
 * and on a Forest world state, which the plugin tells apart from Besu's data storage configuration.
 */
class GraphQLPluginEndToEndTest {

  private static final String[] ARGS = {"--graphql-http-enabled", "--graphql-http-port=0"};
  private static final int MAX_LOG_FILTER_ADDRESSES = 2;
  private static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  @TempDir private static Path dataDir;

  private static final Map<DataStorageFormat, TestChainServices> CHAINS =
      new EnumMap<>(DataStorageFormat.class);
  private static final Map<DataStorageFormat, Node> STARTED_NODES =
      new EnumMap<>(DataStorageFormat.class);
  private static final OkHttpClient CLIENT = new OkHttpClient();

  @CommandLine.Command(name = "besu")
  static class TestCommand implements Runnable {
    @Override
    public void run() {}
  }

  /**
   * As much of a Besu node as the plugin can see: the command line, the service manager and the
   * plugin lifecycle calls.
   */
  static final class Node implements AutoCloseable {
    private final DataStorageFormat format;
    private final GraphQLPlugin plugin = new GraphQLPlugin();
    private final CommandLine commandLine = new CommandLine(new TestCommand());
    private final ServiceManager.SimpleServiceManager serviceManager =
        new ServiceManager.SimpleServiceManager();
    private final NetworkEndpointServiceImpl networkEndpointService =
        new NetworkEndpointServiceImpl();

    Node(final DataStorageFormat format) {
      this.format = format;
      // the services Besu offers to plugins when it registers them
      serviceManager.addService(PicoCLIOptions.class, this::addPicoCLIOptions);
      serviceManager.addService(NetworkEndpointService.class, networkEndpointService);
      serviceManager.addService(RpcConfiguration.class, rpcConfiguration());
    }

    // Same as Besu's PicoCLIOptionsImpl with the --graphql- prefix allowed for the built-in plugin.
    private void addPicoCLIOptions(final String namespace, final Object optionObject) {
      final CommandSpec mixin = CommandSpec.forAnnotatedObject(optionObject);
      for (final OptionSpec optionSpec : mixin.options()) {
        for (final String name : optionSpec.names()) {
          assertThat(name).startsWith("--graphql-");
        }
      }
      commandLine.getCommandSpec().addMixin("Plugin " + namespace, mixin);
    }

    private static RpcConfiguration rpcConfiguration() {
      final RpcConfiguration rpcConfiguration = mock(RpcConfiguration.class);
      when(rpcConfiguration.getConfiguredHostAllowlist()).thenReturn(List.of("127.0.0.1"));
      when(rpcConfiguration.getDefaultHostAddress()).thenReturn("127.0.0.1");
      when(rpcConfiguration.getConfiguredRpcHttpTimeoutSec()).thenReturn(30L);
      when(rpcConfiguration.getConfiguredMaxLogFilterAddresses())
          .thenReturn(MAX_LOG_FILTER_ADDRESSES);
      return rpcConfiguration;
    }

    private BesuConfiguration besuConfiguration() {
      final DataStorageConfiguration dataStorageConfiguration =
          mock(DataStorageConfiguration.class);
      when(dataStorageConfiguration.getDatabaseFormat()).thenReturn(format);
      final BesuConfiguration besuConfiguration = mock(BesuConfiguration.class);
      when(besuConfiguration.getDataPath()).thenReturn(dataDir);
      when(besuConfiguration.getDataStorageConfiguration()).thenReturn(dataStorageConfiguration);
      return besuConfiguration;
    }

    void register() {
      plugin.register(serviceManager);
    }

    void parse(final String... args) {
      commandLine.parseArgs(args);
    }

    /** Adds the runtime services and starts the plugin, as Besu's startPlugins does. */
    void start() {
      plugin.beforeExternalServices();
      serviceManager.addService(BesuConfiguration.class, besuConfiguration());
      chain(format).addTo(serviceManager);
      plugin.start();
      plugin.afterExternalServicePostMainLoop();
    }

    NetworkEndpoint graphQLEndpoint() {
      final List<NetworkEndpoint> endpoints =
          networkEndpointService.getEndpoints().stream()
              .filter(endpoint -> "graphQL".equals(endpoint.getServiceName()))
              .toList();
      assertThat(endpoints).hasSize(1);
      return endpoints.get(0);
    }

    String url() {
      return "http://127.0.0.1:" + graphQLEndpoint().getListeningPort().orElseThrow() + "/graphql";
    }

    @Override
    public void close() {
      plugin.stop();
    }
  }

  private static TestChainServices chain(final DataStorageFormat format) {
    return CHAINS.computeIfAbsent(format, TestChainServices::forHiveTesting);
  }

  private static synchronized Node startedNode(final DataStorageFormat format) {
    return STARTED_NODES.computeIfAbsent(
        format,
        f -> {
          final Node node = new Node(f);
          node.register();
          node.parse(ARGS);
          node.start();
          return node;
        });
  }

  @AfterAll
  static void stopNodes() {
    STARTED_NODES.values().forEach(Node::close);
    STARTED_NODES.clear();
    CHAINS.clear();
    CLIENT.dispatcher().executorService().shutdown();
    CLIENT.connectionPool().evictAll();
  }

  static Stream<Arguments> specs() {
    return Stream.of(DataStorageFormat.BONSAI, DataStorageFormat.FOREST)
        .flatMap(
            format ->
                Stream.concat(
                        GraphQLSpecFixtures.list(GraphQLSpecFixtures.SPEC_DIRECTORY).stream(),
                        GraphQLSpecFixtures.list(GraphQLSpecFixtures.EXTRA_DIRECTORY).stream())
                    .map(fixture -> Arguments.of(format, fixture)));
  }

  @ParameterizedTest(name = "{index}: {0} {1}")
  @MethodSource("specs")
  void servesTheSpecFixtures(final DataStorageFormat format, final String fixture)
      throws IOException {
    GraphQLSpecFixtures.assertFixture(CLIENT, startedNode(format).url(), fixture);
  }

  @Test
  void lifecycle() throws Exception {
    try (Node node = new Node(DataStorageFormat.BONSAI)) {
      node.register();
      final NetworkEndpoint endpoint = node.graphQLEndpoint();
      assertThat(endpoint.getPortsFileKey()).isEqualTo("graphql-http");

      node.parse(ARGS);
      assertThat(endpoint.isEnabled()).isTrue();
      assertThat(endpoint.getHost()).isEqualTo("127.0.0.1");
      assertThat(endpoint.getConfiguredPort()).isZero();
      assertThat(endpoint.getListeningPort()).isEmpty();

      node.start();
      final int port = endpoint.getListeningPort().orElseThrow();
      assertThat(port).isPositive();
      final JsonObject response = query(node.url(), "{block(number: 32) {number}}");
      assertThat(response.getJsonObject("data").getJsonObject("block").getString("number"))
          .isEqualTo("0x20");

      node.plugin.stop();
      assertThat(endpoint.getListeningPort()).isEmpty();
      try (ServerSocket serverSocket = new ServerSocket()) {
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
      }
    }
  }

  @Test
  void disabledByDefault() {
    try (Node node = new Node(DataStorageFormat.BONSAI)) {
      node.register();
      node.parse();
      node.start();

      assertThat(node.graphQLEndpoint().isEnabled()).isFalse();
      assertThat(node.graphQLEndpoint().getConfiguredPort()).isEqualTo(8547);
      assertThat(node.graphQLEndpoint().getListeningPort()).isEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = DataStorageFormat.class,
      names = {"BONSAI", "FOREST"})
  void hostsAllowlistComesFromTheRpcConfiguration(final DataStorageFormat format)
      throws IOException {
    final Request request =
        new Request.Builder()
            .post(RequestBody.create("{gasPrice}", GRAPHQL))
            .url(startedNode(format).url())
            .header("Host", "localhost")
            .build();
    try (Response response = CLIENT.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(403);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = DataStorageFormat.class,
      names = {"BONSAI", "FOREST"})
  void maxLogFilterAddressesComesFromTheRpcConfiguration(final DataStorageFormat format)
      throws IOException {
    final String url = startedNode(format).url();
    final String twoAddresses =
        "\"0x0000000000000000000000000000000000000001\","
            + " \"0x0000000000000000000000000000000000000002\"";

    final JsonObject allowed =
        query(url, "{logs(filter: {addresses: [" + twoAddresses + "]}) {index}}");
    assertThat(allowed.containsKey("errors")).isFalse();
    assertThat(allowed.getJsonObject("data").getJsonArray("logs")).isEmpty();

    final JsonObject rejected =
        query(
            url,
            "{logs(filter: {addresses: ["
                + twoAddresses
                + ", \"0x0000000000000000000000000000000000000003\"]}) {index}}");
    final JsonArray errors = rejected.getJsonArray("errors");
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString("message"))
        .isEqualTo("Exception while fetching data (/logs) : Invalid params");
  }

  private static JsonObject query(final String url, final String query) throws IOException {
    final Request request =
        new Request.Builder().post(RequestBody.create(query, GRAPHQL)).url(url).build();
    try (Response response = CLIENT.newCall(request).execute()) {
      return new JsonObject(response.body().string());
    }
  }
}
