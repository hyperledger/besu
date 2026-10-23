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

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.CoreConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.rpc.RpcConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.NetworkEndpointService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.WorldStateService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * The built-in GraphQL plugin. It serves Besu's GraphQL API over its own HTTP server, reading all
 * data through the plugin services, with the same command line options, endpoint and responses as
 * when the GraphQL service was part of Besu's core.
 *
 * <p>Besu registers and starts this plugin itself, so it is not discoverable as a service.
 */
public class GraphQLPlugin implements BesuPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(GraphQLPlugin.class);

  /** The namespace of the plugin's command line options. */
  public static final String NAME = "graphql";

  /** The name of the service in the {@code net_services} JSON-RPC result. */
  public static final String SERVICE_NAME = "graphQL";

  /** The key of the service in the ports file. */
  public static final String PORTS_FILE_KEY = "graphql-http";

  private static final String SERVICE_NAME_FOR_ERRORS = "graphQLHttp";
  private static final String DEFAULT_HOST_ADDRESS = "127.0.0.1";

  private final GraphQLCliOptions cliOptions = new GraphQLCliOptions();

  private ServiceManager serviceManager;
  private boolean optionsValidated;
  private GraphQLConfiguration configuration;
  private Vertx vertx;
  private GraphQLHttpService graphQLHttpService;

  /** Creates the plugin. */
  public GraphQLPlugin() {}

  @Override
  public void register(final ServiceManager serviceManager) {
    this.serviceManager = serviceManager;
    serviceManager
        .getService(PicoCLIOptions.class)
        .ifPresentOrElse(
            picoCLIOptions -> picoCLIOptions.addPicoCLIOptions(NAME, cliOptions),
            () -> LOG.warn("No command line options service, the GraphQL options are unavailable"));
    serviceManager
        .getService(NetworkEndpointService.class)
        .ifPresent(service -> service.registerEndpoint(new GraphQLEndpoint()));
  }

  /**
   * Validates the GraphQL options once the command line is parsed: a negative maximum block range
   * is rejected with the same error as before the GraphQL service became a plugin, and the options
   * that have no effect are logged. Besu may call this while it validates its own options, so that
   * the error is reported at the same point as before; otherwise it is called before the external
   * services start, or at the latest when the plugin starts. Only the first successful call has an
   * effect. The warnings about options that have no effect are logged with this plugin's logger.
   *
   * @throws CommandLine.ParameterException if an option value is invalid
   * @throws IllegalArgumentException if an option value is invalid and the command line the options
   *     belong to cannot be found
   */
  public void validateOptions() {
    validateOptions(LOG);
  }

  /**
   * Validates the GraphQL options as {@link #validateOptions()} does, logging the warnings about
   * options that have no effect with the given logger. Besu passes its own logger, so these
   * warnings are logged as they were before the GraphQL service became a plugin.
   *
   * <p>If the command line the options belong to cannot be found, a negative maximum block range is
   * still rejected, with the same message, and the dependency warnings are skipped. The options
   * only count as validated once the checks have passed.
   *
   * @param logger the logger for the warnings about options that have no effect
   * @throws CommandLine.ParameterException if an option value is invalid
   * @throws IllegalArgumentException if an option value is invalid and the command line the options
   *     belong to cannot be found
   */
  public synchronized void validateOptions(final Logger logger) {
    if (optionsValidated) {
      return;
    }
    final GraphQlOptions graphQlOptions = cliOptions.getGraphQlOptions();
    final CommandLine commandLine = cliOptions.getCommandLine();
    if (commandLine != null) {
      graphQlOptions.validate(logger, commandLine);
    } else {
      graphQlOptions.validateWithoutCommandLine();
    }
    optionsValidated = true;
  }

  @Override
  public void beforeExternalServices() {
    validateOptions();
  }

  @Override
  public synchronized void start() {
    if (graphQLHttpService != null) {
      return;
    }
    validateOptions();
    if (!isEnabled()) {
      LOG.debug("GraphQL HTTP service is disabled");
      return;
    }
    final GraphQLConfiguration config = buildConfiguration();

    final BesuConfiguration besuConfiguration = getService(BesuConfiguration.class);
    final Path dataDir =
        Optional.ofNullable(besuConfiguration.getDataPath())
            .orElseGet(() -> getService(CoreConfiguration.class).getDataPath());

    final GraphQLDataFetchers dataFetchers =
        new GraphQLDataFetchers(config.getMaxBlockRange(), maxFilterAddresses());
    final GraphQL graphQL;
    try {
      graphQL = GraphQLProvider.buildGraphQL(dataFetchers);
    } catch (final IOException ioe) {
      throw new RuntimeException(ioe);
    }

    final Map<GraphQLContextType, Object> graphQlContextMap =
        Map.of(
            GraphQLContextType.BLOCKCHAIN_SERVICE,
            getService(BlockchainService.class),
            GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE,
            getService(BlockchainQueryService.class),
            GraphQLContextType.WORLD_STATE_SERVICE,
            getService(WorldStateService.class),
            GraphQLContextType.TRANSACTION_SIMULATION_SERVICE,
            getService(TransactionSimulationService.class),
            GraphQLContextType.TRANSACTION_POOL_SERVICE,
            getService(TransactionPoolService.class),
            GraphQLContextType.RLP_CONVERTER_SERVICE,
            getService(RlpConverterService.class),
            GraphQLContextType.SYNCHRONIZATION_SERVICE,
            getService(SynchronizationService.class),
            GraphQLContextType.BONSAI_WORLD_STATE,
            isBonsaiWorldState(besuConfiguration));

    // Nothing may leak the Vert.x threads when the service cannot be created or started, as the
    // plugin may be started again, as on an Ephemery restart.
    final Vertx newVertx =
        Vertx.builder().with(new VertxOptions().setPreferNativeTransport(true)).build();
    try {
      final GraphQLHttpService service =
          new GraphQLHttpService(newVertx, dataDir, config, graphQL, graphQlContextMap);
      waitForServiceToStart(service.start());
      this.configuration = config;
      this.vertx = newVertx;
      this.graphQLHttpService = service;
    } catch (final RuntimeException | Error e) {
      closeAfterFailure(newVertx, e);
      throw e;
    }
  }

  private static void closeAfterFailure(final Vertx failedVertx, final Throwable failure) {
    try {
      failedVertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      failure.addSuppressed(e);
    } catch (final ExecutionException | TimeoutException | RuntimeException e) {
      failure.addSuppressed(e);
    }
  }

  @Override
  public synchronized void stop() {
    final GraphQLHttpService service = graphQLHttpService;
    final Vertx serviceVertx = vertx;
    graphQLHttpService = null;
    vertx = null;
    try {
      if (service != null) {
        service.stop().join();
      }
    } finally {
      if (serviceVertx != null) {
        serviceVertx.close().toCompletionStage().toCompletableFuture().join();
      }
    }
  }

  /**
   * Whether the GraphQL HTTP service is enabled by the command line options.
   *
   * @return true if the service is enabled
   */
  public boolean isEnabled() {
    return Boolean.TRUE.equals(cliOptions.getGraphQlOptions().isGraphQLHttpEnabled());
  }

  /**
   * Returns the configuration the GraphQL HTTP service was started with, or would be started with
   * now.
   *
   * @return the configuration
   */
  public synchronized GraphQLConfiguration getConfiguration() {
    return graphQLHttpService != null ? configuration : buildConfiguration();
  }

  /**
   * Returns the port the GraphQL HTTP service is listening on.
   *
   * @return the listening port, empty if the service is not running
   */
  public synchronized Optional<Integer> getListeningPort() {
    return Optional.ofNullable(graphQLHttpService).map(s -> s.socketAddress().getPort());
  }

  /**
   * Returns the command line options of the plugin.
   *
   * @return the command line options
   */
  public GraphQLCliOptions getCliOptions() {
    return cliOptions;
  }

  private GraphQLConfiguration buildConfiguration() {
    final Optional<RpcConfiguration> rpcConfiguration = findService(RpcConfiguration.class);
    final List<String> hostsAllowlist =
        rpcConfiguration
            .map(RpcConfiguration::getConfiguredHostAllowlist)
            .orElse(List.of("localhost", DEFAULT_HOST_ADDRESS));
    final long timeoutSec =
        rpcConfiguration
            .map(RpcConfiguration::getConfiguredRpcHttpTimeoutSec)
            .orElse(GraphQLConfiguration.DEFAULT_HTTP_TIMEOUT_SEC);
    return cliOptions
        .getGraphQlOptions()
        .graphQLConfiguration(hostsAllowlist, defaultHostAddress(), timeoutSec);
  }

  private String defaultHostAddress() {
    return findService(RpcConfiguration.class)
        .map(RpcConfiguration::getDefaultHostAddress)
        .orElse(DEFAULT_HOST_ADDRESS);
  }

  private int maxFilterAddresses() {
    return findService(RpcConfiguration.class)
        .map(RpcConfiguration::getConfiguredMaxLogFilterAddresses)
        .orElse(GraphQLDataFetchers.DEFAULT_MAX_FILTER_ADDRESSES);
  }

  private static boolean isBonsaiWorldState(final BesuConfiguration besuConfiguration) {
    final DataStorageConfiguration dataStorageConfiguration =
        besuConfiguration.getDataStorageConfiguration();
    return dataStorageConfiguration != null
        && dataStorageConfiguration.getDatabaseFormat() != null
        && dataStorageConfiguration.getDatabaseFormat().isBonsaiFormat();
  }

  private <T extends BesuService> Optional<T> findService(final Class<T> serviceType) {
    return serviceManager == null ? Optional.empty() : serviceManager.getService(serviceType);
  }

  private <T extends BesuService> T getService(final Class<T> serviceType) {
    return findService(serviceType)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "The GraphQL HTTP service requires the "
                        + serviceType.getSimpleName()
                        + " service, which is not available"));
  }

  // Same as Besu's Runner.waitForServiceToStart, which made a failure to start fatal.
  private static void waitForServiceToStart(final CompletableFuture<?> startFuture) {
    do {
      try {
        startFuture.get(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for service to start", e);
      } catch (final ExecutionException e) {
        throw new IllegalStateException(
            "Service " + SERVICE_NAME_FOR_ERRORS + " failed to start", e);
      } catch (final TimeoutException e) {
        LOG.warn("Service {} is taking an unusually long time to start", SERVICE_NAME_FOR_ERRORS);
      }
    } while (!startFuture.isDone());
  }

  /** The GraphQL HTTP listener, as reported to Besu for its port checks and the ports file. */
  private class GraphQLEndpoint implements NetworkEndpointService.NetworkEndpoint {

    @Override
    public String getServiceName() {
      return SERVICE_NAME;
    }

    @Override
    public String getPortsFileKey() {
      return PORTS_FILE_KEY;
    }

    @Override
    public boolean isEnabled() {
      return GraphQLPlugin.this.isEnabled();
    }

    @Override
    public String getHost() {
      return cliOptions.getGraphQlOptions().getGraphQLHttpHost(defaultHostAddress());
    }

    @Override
    public int getConfiguredPort() {
      return cliOptions.getGraphQlOptions().getGraphQLHttpPort();
    }

    @Override
    public Optional<Integer> getListeningPort() {
      return GraphQLPlugin.this.getListeningPort();
    }
  }
}
