/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.quickstart;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.PantheonInfo;
import tech.pegasys.pantheon.tests.quickstart.DockerQuickstartTest.Service.ExposedPort;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocket;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Async;

public class DockerQuickstartTest {

  private static final String PROJECT_ROOT =
      Paths.get(System.getProperty("user.dir")).getParent().toString();
  private static final String DOCKER_COMPOSE_PROJECT_PREFIX = "quickstart_";
  private static final int DEFAULT_HTTP_RPC_PORT = 8545;
  private static final int DEFAULT_WS_RPC_PORT = 8546;
  private static final String DEFAULT_RPC_HOST =
      Optional.ofNullable(System.getenv("DOCKER_PORT_2375_TCP_ADDR")).orElse("localhost");
  private static final String DEFAULT_HTTP_RPC_HOST = "http://" + DEFAULT_RPC_HOST;
  private final Map<ServicesIdentifier, Service> services = new EnumMap<>(ServicesIdentifier.class);
  private final Map<EndpointsIdentifier, String> endpoints =
      new EnumMap<>(EndpointsIdentifier.class);
  private Web3j web3HttpClient;

  @BeforeClass
  public static void runPantheonPrivateNetwork() throws IOException, InterruptedException {
    final ProcessBuilder processBuilder =
        new ProcessBuilder("quickstart/runPantheonPrivateNetwork.sh");
    processBuilder.directory(new File(PROJECT_ROOT)); // going up one level is the project root
    processBuilder.inheritIO(); // redirect all output to logs
    final Process process = processBuilder.start();

    final int exitValue = process.waitFor();

    if (exitValue != 0) {
      // check for errors, error messages and causes are redirected to logs already
      throw new RuntimeException("execution of script failed!");
    }
  }

  @Before
  public void listQuickstartServices() throws IOException, InterruptedException {
    final ProcessBuilder processBuilder =
        new ProcessBuilder("quickstart/listQuickstartServices.sh");
    processBuilder.directory(new File(PROJECT_ROOT)); // going up one level is the project root
    // redirect only error output to logs as we want to be able to
    // keep the standard output available for reading
    processBuilder.redirectError(Redirect.INHERIT);
    final Process process = processBuilder.start();

    final int exitValue = process.waitFor();

    if (exitValue != 0) {
      // check for errors, error messages and causes are redirected to logs already
      throw new RuntimeException("execution of script failed!");
    }

    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));

    reader.lines().forEach(this::populateServicesAndEndpoints);
    reader.close();

    assertThat(services).isNotNull().isNotEmpty();
    assertThat(endpoints).isNotNull().isNotEmpty();

    web3HttpClient =
        Web3j.build(
            new HttpService(
                DEFAULT_HTTP_RPC_HOST
                    + ":"
                    + services
                        .get(ServicesIdentifier.RPCNODE)
                        .exposedPorts
                        .get(DEFAULT_HTTP_RPC_PORT)
                        .externalPort),
            2000,
            Async.defaultExecutorService());

    assertThat(web3HttpClient).isNotNull();
  }

  private void populateServicesAndEndpoints(final String line) {
    // We check that the output of the script displays the right endpoints and services states
    // each endpoint and service will be stored in a map for later use.

    for (final ServicesIdentifier servicesIdentifier : ServicesIdentifier.values()) {
      final Matcher regexMatcher = servicesIdentifier.pattern.matcher(line);
      if (regexMatcher.find()) {
        final Service service = new Service();
        service.name = regexMatcher.group(1);
        service.state = regexMatcher.group(2).toLowerCase();
        final String[] portMappings = regexMatcher.group(3).split(",", -1);
        for (final String mapping : portMappings) {
          final ExposedPort port = new ExposedPort(mapping);
          service.exposedPorts.put(port.internalPort, port);
        }
        services.put(servicesIdentifier, service);
      }
    }

    for (final EndpointsIdentifier endpointsIdentifier : EndpointsIdentifier.values()) {
      final Matcher regexMatcher = endpointsIdentifier.pattern.matcher(line);
      if (regexMatcher.find()) {
        final String endpoint = regexMatcher.group(1);
        endpoints.put(endpointsIdentifier, endpoint);
      }
    }
  }

  @After
  public void closeConnections() {
    assertThat(web3HttpClient).isNotNull();
    web3HttpClient.shutdown();
  }

  @AfterClass
  public static void removePantheonPrivateNetwork() throws IOException, InterruptedException {
    final ProcessBuilder processBuilder =
        new ProcessBuilder("quickstart/removePantheonPrivateNetwork.sh");
    processBuilder.inheritIO(); // redirect all output to logs
    processBuilder.directory(new File(PROJECT_ROOT)); // going up one level is the project root
    final Process process = processBuilder.start();

    final int exitValue = process.waitFor();
    if (exitValue != 0) {
      // check for errors, all output and then also error messages and causes are redirected to logs
      throw new RuntimeException("execution of script failed!");
    }
  }

  @Test
  public void servicesShouldBeUp() {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Arrays.stream(ServicesIdentifier.values())
                    .forEach(
                        servicesIdentifier ->
                            assertThat(services.get(servicesIdentifier).state).isEqualTo("up")));
  }

  @Test
  public void servicesEndpointsShouldBeExposed() {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Arrays.stream(EndpointsIdentifier.values())
                    .forEach(
                        endpointsIdentifier ->
                            assertThat(endpoints.get(endpointsIdentifier))
                                .isNotNull()
                                .isNotEmpty()));
  }

  @Test
  public void rpcNodeShouldFindPeers() {
    // Peers are those defined in docker-compose.yml and launched with scaling of 4 regular nodes
    // which gives us 6 peers of the RPC node: bootnode, minernode and 4 regular nodes.
    final int expectecNumberOfPeers = 6;

    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(web3HttpClient.netPeerCount().send().getQuantity().intValueExact())
                    .isEqualTo(expectecNumberOfPeers));
  }

  @Test
  public void rpcNodeShouldReturnCorrectVersion() {
    final String expectedVersion = PantheonInfo.version();
    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(web3HttpClient.web3ClientVersion().send().getWeb3ClientVersion())
                    .isEqualTo(expectedVersion));
  }

  @Test
  public void mustMineSomeBlocks() {
    // A bug occurred that failed mining after 2 blocks, so testing at least 10.
    final int expectedAtLeastBlockNumber = 10;
    Awaitility.await()
        .ignoreExceptions()
        .atMost(5, TimeUnit.MINUTES)
        .untilAsserted(
            () ->
                assertThat(web3HttpClient.ethBlockNumber().send().getBlockNumber().intValueExact())
                    .isGreaterThan(expectedAtLeastBlockNumber));
  }

  @Test
  public void webSocketRpcServiceMustConnect() {
    final RequestOptions options = new RequestOptions();
    options.setPort(
        services
            .get(ServicesIdentifier.RPCNODE)
            .exposedPorts
            .get(DEFAULT_WS_RPC_PORT)
            .externalPort);
    options.setHost(DEFAULT_RPC_HOST);

    final WebSocket[] wsConnection = new WebSocket[1];
    final Vertx vertx = Vertx.vertx();
    try {
      vertx
          .createHttpClient(new HttpClientOptions())
          .websocket(options, websocket -> wsConnection[0] = websocket);

      Awaitility.await()
          .ignoreExceptions()
          .atMost(30, TimeUnit.SECONDS)
          .untilAsserted(() -> assertThat(wsConnection[0]).isNotNull());
    } finally {
      assertThat(wsConnection[0]).isNotNull();
      wsConnection[0].close();
    }
  }

  private enum ServicesIdentifier {
    BOOTNODE,
    EXPLORER,
    MINERNODE,
    NODE,
    RPCNODE;

    final Pattern pattern;

    ServicesIdentifier() {
      pattern =
          Pattern.compile(
              "(^"
                  + DOCKER_COMPOSE_PROJECT_PREFIX
                  + this.name().toLowerCase()
                  + "_[0-9]+)\\s+.+\\s{3,}(\\w+)\\s+(.+)",
              Pattern.DOTALL);
    }
  }

  private enum EndpointsIdentifier {
    EXPLORER("Web block explorer address"),
    HTTP_RPC("JSON-RPC.+HTTP.+service endpoint"),
    WS_RPC("JSON-RPC.+WebSocket.+service endpoint");

    final Pattern pattern;

    EndpointsIdentifier(final String lineLabel) {
      pattern = Pattern.compile(lineLabel + ".+(http://.+:[0-9]+)", Pattern.DOTALL);
    }
  }

  static class Service {

    String name;
    String state;
    Map<Integer, ExposedPort> exposedPorts = new HashMap<>();

    static class ExposedPort {

      final Integer internalPort;
      final Integer externalPort;
      private final Pattern pattern = Pattern.compile("[0-9]+", Pattern.DOTALL);

      ExposedPort(final String portDescription) {
        if (portDescription.contains("->")) {
          final String[] ports = portDescription.split("->", 2);

          final String[] internalPortInfos = ports[1].split("/", 2);
          internalPort = Integer.valueOf(internalPortInfos[0]);
          final String[] externalPortInfos = ports[0].split(":", 2);

          externalPort = Integer.valueOf(externalPortInfos[1]);
        } else {
          final Matcher regexMatcher = pattern.matcher(portDescription);
          internalPort = regexMatcher.find() ? Integer.valueOf(regexMatcher.group(0)) : null;
          externalPort = null;
        }
      }
    }
  }
}
