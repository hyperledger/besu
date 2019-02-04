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
package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import static tech.pegasys.pantheon.cli.NetworkName.MAINNET;

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.cli.PantheonControllerBuilder;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThreadPantheonNodeRunner implements PantheonNodeRunner {

  private static final int NETWORK_ID = 10;

  private final Logger LOG = LogManager.getLogger();
  private final Map<String, Runner> pantheonRunners = new HashMap<>();
  private ExecutorService nodeExecutor = Executors.newCachedThreadPool();

  @Override
  public void startNode(final PantheonNode node) {
    if (nodeExecutor == null || nodeExecutor.isShutdown()) {
      nodeExecutor = Executors.newCachedThreadPool();
    }

    final MetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();
    final PantheonControllerBuilder builder = new PantheonControllerBuilder();
    final EthNetworkConfig ethNetworkConfig =
        node.ethNetworkConfig()
            .orElse(
                new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(MAINNET))
                    .setNetworkId(NETWORK_ID)
                    .build());
    final PantheonController<?> pantheonController;
    try {
      pantheonController =
          builder
              .synchronizerConfiguration(new SynchronizerConfiguration.Builder().build())
              .homePath(node.homeDirectory())
              .ethNetworkConfig(ethNetworkConfig)
              .syncWithOttoman(false)
              .miningParameters(node.getMiningParameters())
              .devMode(node.isDevMode())
              .nodePrivateKeyFile(KeyPairUtil.getDefaultKeyFile(node.homeDirectory()))
              .metricsSystem(noOpMetricsSystem)
              .privacyParameters(PrivacyParameters.noPrivacy())
              .build();
    } catch (final IOException e) {
      throw new RuntimeException("Error building PantheonController", e);
    }

    RunnerBuilder runnerBuilder = new RunnerBuilder();
    node.getPermissioningConfiguration().ifPresent(runnerBuilder::permissioningConfiguration);

    final Runner runner =
        runnerBuilder
            .vertx(Vertx.vertx())
            .pantheonController(pantheonController)
            .discovery(node.isDiscoveryEnabled())
            .bootstrapPeers(node.bootnodes())
            .discoveryHost(node.hostName())
            .discoveryPort(node.p2pPort())
            .maxPeers(25)
            .jsonRpcConfiguration(node.jsonRpcConfiguration())
            .webSocketConfiguration(node.webSocketConfiguration())
            .dataDir(node.homeDirectory())
            .bannedNodeIds(Collections.emptySet())
            .metricsSystem(noOpMetricsSystem)
            .metricsConfiguration(node.metricsConfiguration())
            .p2pEnabled(node.p2pEnabled())
            .build();

    nodeExecutor.submit(runner::execute);

    waitForPortsFile(node.homeDirectory().toAbsolutePath());

    pantheonRunners.put(node.getName(), runner);
  }

  @Override
  public void stopNode(final PantheonNode node) {
    node.stop();
    killRunner(node.getName());
  }

  @Override
  public void shutdown() {
    // iterate over a copy of the set so that pantheonRunner can be updated when a runner is killed
    new HashSet<>(pantheonRunners.keySet()).forEach(this::killRunner);
    try {
      nodeExecutor.shutdownNow();
      if (!nodeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Failed to shut down node executor");
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isActive(final String nodeName) {
    return pantheonRunners.containsKey(nodeName);
  }

  private void killRunner(final String name) {
    LOG.info("Killing " + name + " runner");

    if (pantheonRunners.containsKey(name)) {
      try {
        pantheonRunners.get(name).close();
        pantheonRunners.remove(name);
      } catch (final Exception e) {
        throw new RuntimeException("Error shutting down node " + name, e);
      }
    }
  }
}
