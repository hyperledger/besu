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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.rpc.RpcConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.NetworkEndpointService;
import org.hyperledger.besu.plugin.services.NetworkEndpointService.NetworkEndpoint;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.WorldStateService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

class GraphQLPluginTest {

  @CommandLine.Command(name = "besu")
  static class TestCommand implements Runnable {
    @Override
    public void run() {}
  }

  static class TestNetworkEndpointService implements NetworkEndpointService {
    private final List<NetworkEndpoint> endpoints = new ArrayList<>();

    @Override
    public void registerEndpoint(final NetworkEndpoint endpoint) {
      endpoints.add(endpoint);
    }

    @Override
    public List<NetworkEndpoint> getEndpoints() {
      return endpoints;
    }
  }

  @TempDir private Path dataDir;

  private final GraphQLPlugin plugin = new GraphQLPlugin();
  private final ServiceManager.SimpleServiceManager serviceManager =
      new ServiceManager.SimpleServiceManager();
  private final TestNetworkEndpointService networkEndpointService =
      new TestNetworkEndpointService();
  private final BlockchainQueryService blockchainQueryService = mock(BlockchainQueryService.class);
  private final OkHttpClient client = new OkHttpClient();
  private CommandLine commandLine;

  @BeforeEach
  void setUp() {
    commandLine = new CommandLine(new TestCommand());
    final PicoCLIOptions picoCLIOptions =
        (namespace, optionObject) ->
            commandLine
                .getCommandSpec()
                .addMixin("Plugin " + namespace, CommandSpec.forAnnotatedObject(optionObject));
    serviceManager.addService(PicoCLIOptions.class, picoCLIOptions);
    serviceManager.addService(NetworkEndpointService.class, networkEndpointService);
  }

  @AfterEach
  void tearDown() {
    plugin.stop();
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
  }

  // The services the plugin reads its data from, available once Besu starts the plugins.
  private void addRuntimeServices() {
    final DataStorageConfiguration dataStorageConfiguration = mock(DataStorageConfiguration.class);
    when(dataStorageConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.BONSAI);
    final BesuConfiguration besuConfiguration = mock(BesuConfiguration.class);
    when(besuConfiguration.getDataPath()).thenReturn(dataDir);
    when(besuConfiguration.getDataStorageConfiguration()).thenReturn(dataStorageConfiguration);
    when(blockchainQueryService.getOracleGasPrice()).thenReturn(Wei.of(0x1234));

    serviceManager.addService(BesuConfiguration.class, besuConfiguration);
    serviceManager.addService(BlockchainService.class, mock(BlockchainService.class));
    serviceManager.addService(BlockchainQueryService.class, blockchainQueryService);
    serviceManager.addService(WorldStateService.class, mock(WorldStateService.class));
    serviceManager.addService(
        TransactionSimulationService.class, mock(TransactionSimulationService.class));
    serviceManager.addService(TransactionPoolService.class, mock(TransactionPoolService.class));
    serviceManager.addService(RlpConverterService.class, mock(RlpConverterService.class));
    serviceManager.addService(SynchronizationService.class, mock(SynchronizationService.class));
  }

  private NetworkEndpoint endpoint() {
    assertThat(networkEndpointService.getEndpoints()).hasSize(1);
    return networkEndpointService.getEndpoints().get(0);
  }

  private String query(final int port, final String query) throws IOException {
    final Request request =
        new Request.Builder()
            .post(RequestBody.create(query, MediaType.parse("application/graphql; charset=utf-8")))
            .url("http://127.0.0.1:" + port + "/graphql")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      return response.body().string();
    }
  }

  @Test
  void registerAddsTheGraphQLOptions() {
    plugin.register(serviceManager);

    assertThat(commandLine.getCommandSpec().findOption("--graphql-http-enabled")).isNotNull();
    assertThat(commandLine.getCommandSpec().findOption("--graphql-http-port")).isNotNull();
    assertThat(commandLine.getCommandSpec().findOption("--graphql-max-blocks-range")).isNotNull();
  }

  @Test
  void registerDeclaresTheGraphQLEndpoint() {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "1234");

    final NetworkEndpoint endpoint = endpoint();
    assertThat(endpoint.getServiceName()).isEqualTo("graphQL");
    assertThat(endpoint.getPortsFileKey()).isEqualTo("graphql-http");
    assertThat(endpoint.isEnabled()).isTrue();
    assertThat(endpoint.getHost()).isEqualTo("127.0.0.1");
    assertThat(endpoint.getConfiguredPort()).isEqualTo(1234);
    assertThat(endpoint.getListeningPort()).isEmpty();
  }

  @Test
  void endpointIsDisabledByDefault() {
    plugin.register(serviceManager);
    commandLine.parseArgs();

    assertThat(endpoint().isEnabled()).isFalse();
    assertThat(endpoint().getConfiguredPort()).isEqualTo(8547);
  }

  @Test
  void startDoesNothingWhenDisabled() {
    plugin.register(serviceManager);
    commandLine.parseArgs();

    // no runtime service is needed when the service is disabled
    plugin.start();

    assertThat(plugin.getListeningPort()).isEmpty();
    assertThat(endpoint().getListeningPort()).isEmpty();
  }

  @Test
  void startServesGraphQLAndReportsTheListeningPort() throws Exception {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    addRuntimeServices();

    plugin.start();

    assertThat(endpoint().getListeningPort()).isPresent();
    final int port = endpoint().getListeningPort().get();
    assertThat(port).isPositive();
    assertThat(plugin.getListeningPort()).contains(port);

    final JsonObject json = new JsonObject(query(port, "{gasPrice}"));
    assertThat(json.getJsonObject("data").getString("gasPrice")).isEqualTo("0x1234");
  }

  @Test
  void stopReleasesThePort() throws Exception {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    addRuntimeServices();
    plugin.start();
    final int port = endpoint().getListeningPort().orElseThrow();

    plugin.stop();

    assertThat(endpoint().getListeningPort()).isEmpty();
    try (ServerSocket serverSocket = new ServerSocket()) {
      serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
      assertThat(serverSocket.isBound()).isTrue();
    }
  }

  @Test
  void stopIsIdempotent() {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    addRuntimeServices();
    plugin.start();

    plugin.stop();
    plugin.stop();

    assertThat(plugin.getListeningPort()).isEmpty();
  }

  @Test
  void stopBeforeStartIsHarmless() {
    plugin.register(serviceManager);

    plugin.stop();

    assertThat(plugin.getListeningPort()).isEmpty();
  }

  @Test
  void startIsIdempotent() {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    addRuntimeServices();
    plugin.start();
    final int port = plugin.getListeningPort().orElseThrow();

    plugin.start();

    assertThat(plugin.getListeningPort()).contains(port);
  }

  @Test
  void canBeStartedAgainAfterStop() throws Exception {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    addRuntimeServices();
    plugin.start();
    plugin.stop();

    plugin.start();

    final int port = plugin.getListeningPort().orElseThrow();
    assertThat(
            new JsonObject(query(port, "{gasPrice}")).getJsonObject("data").getString("gasPrice"))
        .isEqualTo("0x1234");
  }

  @Test
  void failureToBindIsFatal() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket()) {
      serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      plugin.register(serviceManager);
      commandLine.parseArgs(
          "--graphql-http-enabled",
          "--graphql-http-port",
          String.valueOf(serverSocket.getLocalPort()));
      addRuntimeServices();

      assertThatThrownBy(plugin::start)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Service graphQLHttp failed to start");
      assertThat(plugin.getListeningPort()).isEmpty();
    }
  }

  @Test
  void missingServiceIsFatal() {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");

    assertThatThrownBy(plugin::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("BesuConfiguration");
  }

  @Test
  void registerWorksWithoutTheOptionalServices() {
    final ServiceManager.SimpleServiceManager emptyServiceManager =
        new ServiceManager.SimpleServiceManager();

    plugin.register(emptyServiceManager);
    plugin.start();

    assertThat(plugin.isEnabled()).isFalse();
    assertThat(plugin.getListeningPort()).isEmpty();
  }

  @Test
  void rpcConfigurationValuesReachTheConfiguration() {
    final RpcConfiguration rpcConfiguration = mock(RpcConfiguration.class);
    when(rpcConfiguration.getConfiguredHostAllowlist()).thenReturn(List.of("example.org"));
    when(rpcConfiguration.getDefaultHostAddress()).thenReturn("0.0.0.0");
    when(rpcConfiguration.getConfiguredRpcHttpTimeoutSec()).thenReturn(42L);
    serviceManager.addService(RpcConfiguration.class, rpcConfiguration);
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled");

    final GraphQLConfiguration configuration = plugin.getConfiguration();

    assertThat(configuration.getHostsAllowlist()).containsExactly("example.org");
    assertThat(configuration.getHost()).isEqualTo("0.0.0.0");
    assertThat(configuration.getHttpTimeoutSec()).isEqualTo(42L);
    assertThat(endpoint().getHost()).isEqualTo("0.0.0.0");
  }

  @Test
  void configurationDefaultsWithoutRpcConfiguration() {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled");

    final GraphQLConfiguration configuration = plugin.getConfiguration();

    assertThat(configuration.getHostsAllowlist()).containsExactly("localhost", "127.0.0.1");
    assertThat(configuration.getHost()).isEqualTo("127.0.0.1");
    assertThat(configuration.getHttpTimeoutSec())
        .isEqualTo(GraphQLConfiguration.DEFAULT_HTTP_TIMEOUT_SEC);
  }

  @Test
  void invalidPortIsFatalAndLeaksNoVertx() throws Exception {
    final Set<Thread> vertxThreadsBefore = vertxThreads();
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "70000");
    addRuntimeServices();

    assertThatThrownBy(plugin::start)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid port configuration.");

    assertThat(plugin.getListeningPort()).isEmpty();
    // The Vert.x created for the service is closed, so none of its threads are left running.
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    Set<Thread> leaked = newVertxThreads(vertxThreadsBefore);
    while (!leaked.isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(50);
      leaked = newVertxThreads(vertxThreadsBefore);
    }
    assertThat(leaked).isEmpty();
  }

  @Test
  void canBeStartedAfterAFailedStart() throws Exception {
    plugin.register(serviceManager);
    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "70000");
    addRuntimeServices();
    assertThatThrownBy(plugin::start).isInstanceOf(IllegalArgumentException.class);

    commandLine.parseArgs("--graphql-http-enabled", "--graphql-http-port", "0");
    plugin.start();

    final int port = plugin.getListeningPort().orElseThrow();
    assertThat(
            new JsonObject(query(port, "{gasPrice}")).getJsonObject("data").getString("gasPrice"))
        .isEqualTo("0x1234");
  }

  // Every Vert.x instance runs a blocked thread checker from creation until it is closed.
  private static Set<Thread> vertxThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(Thread::isAlive)
        .filter(thread -> thread.getName().startsWith("vertx-blocked-thread-checker"))
        .collect(Collectors.toSet());
  }

  private static Set<Thread> newVertxThreads(final Set<Thread> before) {
    final Set<Thread> threads = vertxThreads();
    threads.removeAll(before);
    return threads;
  }
}
