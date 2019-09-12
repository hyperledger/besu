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

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.cli.options.NetworkingOptions;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.plugin.services.metrics.MetricCategory;
import tech.pegasys.pantheon.tests.acceptance.dsl.StaticNodesUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

public class ProcessPantheonNodeRunner implements PantheonNodeRunner {

  private final Logger LOG = LogManager.getLogger();
  private final Logger PROCESS_LOG = LogManager.getLogger("tech.pegasys.pantheon.SubProcessLog");

  private final Map<String, Process> pantheonProcesses = new HashMap<>();
  private final ExecutorService outputProcessorExecutor = Executors.newCachedThreadPool();

  ProcessPantheonNodeRunner() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  @Override
  public void startNode(final PantheonNode node) {
    final Path dataDir = node.homeDirectory();

    final List<String> params = new ArrayList<>();
    params.add("build/install/pantheon/bin/pantheon");

    params.add("--data-path");
    params.add(dataDir.toAbsolutePath().toString());

    if (node.isDevMode()) {
      params.add("--network");
      params.add("DEV");
    }

    params.add("--discovery-enabled");
    params.add(Boolean.toString(node.isDiscoveryEnabled()));

    params.add("--p2p-host");
    params.add(node.p2pListenHost());

    params.add("--p2p-port");
    params.add("0");

    if (node.getMiningParameters().isMiningEnabled()) {
      params.add("--miner-enabled");
      params.add("--miner-coinbase");
      params.add(node.getMiningParameters().getCoinbase().get().toString());
    }

    if (node.getPrivacyParameters().isEnabled()) {
      params.add("--privacy-enabled");
      params.add("--privacy-url");
      params.add(node.getPrivacyParameters().getEnclaveUri().toString());
      params.add("--privacy-public-key-file");
      params.add(node.getPrivacyParameters().getEnclavePublicKeyFile().getAbsolutePath());
      params.add("--privacy-precompiled-address");
      params.add(String.valueOf(node.getPrivacyParameters().getPrivacyAddress()));
      params.add("--privacy-marker-transaction-signing-key-file");
      params.add(node.homeDirectory().resolve("key").toString());
    }

    params.add("--bootnodes");

    if (!node.getBootnodes().isEmpty()) {
      params.add(node.getBootnodes().stream().map(URI::toString).collect(Collectors.joining(",")));
    }

    if (node.hasStaticNodes()) {
      createStaticNodes(node);
    }

    if (node.isJsonRpcEnabled()) {
      params.add("--rpc-http-enabled");
      params.add("--rpc-http-host");
      params.add(node.jsonRpcListenHost().get());
      params.add("--rpc-http-port");
      params.add(node.jsonRpcListenPort().map(Object::toString).get());
      params.add("--rpc-http-api");
      params.add(apiList(node.jsonRpcConfiguration().getRpcApis()));
      if (node.jsonRpcConfiguration().isAuthenticationEnabled()) {
        params.add("--rpc-http-authentication-enabled");
      }
      if (node.jsonRpcConfiguration().getAuthenticationCredentialsFile() != null) {
        params.add("--rpc-http-authentication-credentials-file");
        params.add(node.jsonRpcConfiguration().getAuthenticationCredentialsFile());
      }
    }

    if (node.wsRpcEnabled()) {
      params.add("--rpc-ws-enabled");
      params.add("--rpc-ws-host");
      params.add(node.wsRpcListenHost().get());
      params.add("--rpc-ws-port");
      params.add(node.wsRpcListenPort().map(Object::toString).get());
      params.add("--rpc-ws-api");
      params.add(apiList(node.webSocketConfiguration().getRpcApis()));
      if (node.webSocketConfiguration().isAuthenticationEnabled()) {
        params.add("--rpc-ws-authentication-enabled");
      }
      if (node.webSocketConfiguration().getAuthenticationCredentialsFile() != null) {
        params.add("--rpc-ws-authentication-credentials-file");
        params.add(node.webSocketConfiguration().getAuthenticationCredentialsFile());
      }
    }

    if (node.isMetricsEnabled()) {
      final MetricsConfiguration metricsConfiguration = node.getMetricsConfiguration();
      params.add("--metrics-enabled");
      params.add("--metrics-host");
      params.add(metricsConfiguration.getHost());
      params.add("--metrics-port");
      params.add(Integer.toString(metricsConfiguration.getPort()));
      for (final MetricCategory category : metricsConfiguration.getMetricCategories()) {
        params.add("--metrics-category");
        params.add(((Enum<?>) category).name());
      }
      if (metricsConfiguration.isPushEnabled()) {
        params.add("--metrics-push-enabled");
        params.add("--metrics-push-host");
        params.add(metricsConfiguration.getPushHost());
        params.add("--metrics-push-port");
        params.add(Integer.toString(metricsConfiguration.getPushPort()));
        params.add("--metrics-push-interval");
        params.add(Integer.toString(metricsConfiguration.getPushInterval()));
        params.add("--metrics-push-prometheus-job");
        params.add(metricsConfiguration.getPrometheusJob());
      }
    }

    node.getGenesisConfig()
        .ifPresent(
            genesis -> {
              final Path genesisFile = createGenesisFile(node, genesis);
              params.add("--genesis-file");
              params.add(genesisFile.toAbsolutePath().toString());
            });

    if (!node.isP2pEnabled()) {
      params.add("--p2p-enabled");
      params.add("false");
    } else {
      final List<String> networkConfigParams =
          NetworkingOptions.fromConfig(node.getNetworkingConfiguration()).getCLIOptions();
      params.addAll(networkConfigParams);
    }

    if (node.isRevertReasonEnabled()) {
      params.add("--revert-reason-enabled");
    }

    node.getPermissioningConfiguration()
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(
            permissioningConfiguration -> {
              if (permissioningConfiguration.isNodeWhitelistEnabled()) {
                params.add("--permissions-nodes-config-file-enabled");
              }
              if (permissioningConfiguration.getNodePermissioningConfigFilePath() != null) {
                params.add("--permissions-nodes-config-file");
                params.add(permissioningConfiguration.getNodePermissioningConfigFilePath());
              }
              if (permissioningConfiguration.isAccountWhitelistEnabled()) {
                params.add("--permissions-accounts-config-file-enabled");
              }
              if (permissioningConfiguration.getAccountPermissioningConfigFilePath() != null) {
                params.add("--permissions-accounts-config-file");
                params.add(permissioningConfiguration.getAccountPermissioningConfigFilePath());
              }
            });

    node.getPermissioningConfiguration()
        .flatMap(PermissioningConfiguration::getSmartContractConfig)
        .ifPresent(
            permissioningConfiguration -> {
              if (permissioningConfiguration.isSmartContractNodeWhitelistEnabled()) {
                params.add("--permissions-nodes-contract-enabled");
              }
              if (permissioningConfiguration.getNodeSmartContractAddress() != null) {
                params.add("--permissions-nodes-contract-address");
                params.add(permissioningConfiguration.getNodeSmartContractAddress().toString());
              }
              if (permissioningConfiguration.isSmartContractAccountWhitelistEnabled()) {
                params.add("--permissions-accounts-contract-enabled");
              }
              if (permissioningConfiguration.getAccountSmartContractAddress() != null) {
                params.add("--permissions-accounts-contract-address");
                params.add(permissioningConfiguration.getAccountSmartContractAddress().toString());
              }
            });
    params.addAll(node.getExtraCLIOptions());

    params.add("--key-value-storage");
    params.add("rocksdb");

    LOG.info("Creating pantheon process with params {}", params);
    final ProcessBuilder processBuilder =
        new ProcessBuilder(params)
            .directory(new File(System.getProperty("user.dir")).getParentFile())
            .redirectErrorStream(true)
            .redirectInput(Redirect.INHERIT);
    if (!node.getPlugins().isEmpty()) {
      processBuilder
          .environment()
          .put(
              "PANTHEON_OPTS",
              "-Dpantheon.plugins.dir=" + dataDir.resolve("plugins").toAbsolutePath().toString());
    }

    try {
      final Process process = processBuilder.start();
      outputProcessorExecutor.execute(() -> printOutput(node, process));
      pantheonProcesses.put(node.getName(), process);
    } catch (final IOException e) {
      LOG.error("Error starting PantheonNode process", e);
    }

    waitForPortsFile(dataDir);
  }

  private void printOutput(final PantheonNode node, final Process process) {
    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
      String line = in.readLine();
      while (line != null) {
        PROCESS_LOG.info("{}: {}", node.getName(), line);
        line = in.readLine();
      }
    } catch (final IOException e) {
      LOG.error("Failed to read output from process", e);
    }
  }

  private Path createGenesisFile(final PantheonNode node, final String genesisConfig) {
    try {
      final Path genesisFile = Files.createTempFile(node.homeDirectory(), "genesis", "");
      genesisFile.toFile().deleteOnExit();
      Files.write(genesisFile, genesisConfig.getBytes(UTF_8));
      return genesisFile;
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void createStaticNodes(final PantheonNode node) {
    StaticNodesUtils.createStaticNodesFile(node.homeDirectory(), node.getStaticNodes());
  }

  private String apiList(final Collection<RpcApi> rpcApis) {
    return rpcApis.stream().map(RpcApis::getValue).collect(Collectors.joining(","));
  }

  @Override
  public void stopNode(final PantheonNode node) {
    node.stop();
    if (pantheonProcesses.containsKey(node.getName())) {
      final Process process = pantheonProcesses.get(node.getName());
      killPantheonProcess(node.getName(), process);
    }
  }

  @Override
  public synchronized void shutdown() {
    final HashMap<String, Process> localMap = new HashMap<>(pantheonProcesses);
    localMap.forEach(this::killPantheonProcess);
    outputProcessorExecutor.shutdown();
    try {
      if (!outputProcessorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.error("Output processor executor did not shutdown cleanly.");
      }
    } catch (final InterruptedException e) {
      LOG.error("Interrupted while already shutting down", e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isActive(final String nodeName) {
    final Process process = pantheonProcesses.get(nodeName);
    return process != null && process.isAlive();
  }

  private void killPantheonProcess(final String name, final Process process) {
    LOG.info("Killing " + name + " process");

    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (process.isAlive()) {
                process.destroy();
                pantheonProcesses.remove(name);
                return false;
              } else {
                pantheonProcesses.remove(name);
                return true;
              }
            });
  }
}
