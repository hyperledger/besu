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
package org.hyperledger.besu.tests.acceptance.dsl.node;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.cli.options.unstable.NetworkingOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.tests.acceptance.dsl.StaticNodesUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ProcessBesuNodeRunner implements BesuNodeRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessBesuNodeRunner.class);
  private static final Logger PROCESS_LOG =
      LoggerFactory.getLogger("org.hyperledger.besu.SubProcessLog");

  private final Map<String, Process> besuProcesses = new HashMap<>();
  private final ExecutorService outputProcessorExecutor = Executors.newCachedThreadPool();
  private boolean capturingConsole;
  private final ByteArrayOutputStream consoleContents = new ByteArrayOutputStream();
  private final PrintStream consoleOut = new PrintStream(consoleContents);

  ProcessBesuNodeRunner() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  @Override
  public void startNode(final BesuNode node) {

    final Path dataDir = node.homeDirectory();

    final List<String> params = new ArrayList<>();
    params.add("build/install/besu/bin/besu");

    params.add("--data-path");
    params.add(dataDir.toAbsolutePath().toString());

    if (node.isDevMode()) {
      params.add("--network");
      params.add("DEV");
    } else if (node.getNetwork() != null) {
      params.add("--network");
      params.add(node.getNetwork().name());
    }

    params.add("--sync-mode");
    params.add("FULL");

    params.add("--discovery-enabled");
    params.add(Boolean.toString(node.isDiscoveryEnabled()));

    params.add("--p2p-host");
    params.add(node.p2pListenHost());

    params.add("--p2p-port");
    params.add(node.getP2pPort());

    if (node.getMiningParameters().isMiningEnabled()) {
      params.add("--miner-enabled");
      params.add("--miner-coinbase");
      params.add(node.getMiningParameters().getCoinbase().get().toString());
      params.add("--miner-stratum-port");
      params.add(Integer.toString(node.getMiningParameters().getStratumPort()));
      params.add("--miner-stratum-host");
      params.add(node.getMiningParameters().getStratumNetworkInterface());
      params.add("--min-gas-price");
      params.add(
          Integer.toString(node.getMiningParameters().getMinTransactionGasPrice().intValue()));
      params.add("--Xminer-remote-sealers-limit");
      params.add(Integer.toString(node.getMiningParameters().getRemoteSealersLimit()));
      params.add("--Xminer-remote-sealers-hashrate-ttl");
      params.add(Long.toString(node.getMiningParameters().getRemoteSealersTimeToLive()));
    }
    if (node.getMiningParameters().isStratumMiningEnabled()) {
      params.add("--miner-stratum-enabled");
    }

    if (node.getPrivacyParameters().isEnabled()) {
      params.add("--privacy-enabled");

      params.add("--privacy-url");
      params.add(node.getPrivacyParameters().getEnclaveUri().toString());

      if (node.getPrivacyParameters().isMultiTenancyEnabled()) {
        params.add("--privacy-multi-tenancy-enabled");
      } else {
        params.add("--privacy-public-key-file");
        params.add(node.getPrivacyParameters().getEnclavePublicKeyFile().getAbsolutePath());
      }

      if (!node.getExtraCLIOptions().contains("--plugin-privacy-service-signing-enabled=true")) {
        params.add("--privacy-marker-transaction-signing-key-file");
        params.add(node.homeDirectory().resolve("key").toString());
      }

      if (node.getPrivacyParameters().isFlexiblePrivacyGroupsEnabled()) {
        params.add("--privacy-flexible-groups-enabled");
      }

      if (node.getPrivacyParameters().isPrivacyPluginEnabled()) {
        params.add("--Xprivacy-plugin-enabled");
      }
    }

    if (!node.getBootnodes().isEmpty()) {
      params.add("--bootnodes");
      params.add(node.getBootnodes().stream().map(URI::toString).collect(Collectors.joining(",")));
    }

    if (node.hasStaticNodes()) {
      createStaticNodes(node);
    }

    if (node.isDnsEnabled()) {
      params.add("--Xdns-enabled");
      params.add("true");
      params.add("--Xdns-update-enabled");
      params.add("true");
    }

    if (node.isJsonRpcEnabled()) {
      params.add("--rpc-http-enabled");
      params.add("--rpc-http-host");
      params.add(node.jsonRpcListenHost().get());
      params.add("--rpc-http-port");
      params.add(node.jsonRpcListenPort().map(Object::toString).get());
      params.add("--rpc-http-api");
      params.add(apiList(node.jsonRpcConfiguration().getRpcApis()));
      if (!node.jsonRpcConfiguration().getNoAuthRpcApis().isEmpty()) {
        params.add("--rpc-http-api-methods-no-auth");
        params.add(apiList(node.jsonRpcConfiguration().getNoAuthRpcApis()));
      }
      if (node.jsonRpcConfiguration().isAuthenticationEnabled()) {
        params.add("--rpc-http-authentication-enabled");
      }
      if (node.jsonRpcConfiguration().getAuthenticationCredentialsFile() != null) {
        params.add("--rpc-http-authentication-credentials-file");
        params.add(node.jsonRpcConfiguration().getAuthenticationCredentialsFile());
      }
      if (node.jsonRpcConfiguration().getAuthenticationPublicKeyFile() != null) {
        params.add("--rpc-http-authentication-jwt-public-key-file");
        params.add(node.jsonRpcConfiguration().getAuthenticationPublicKeyFile().getAbsolutePath());
      }
      if (node.jsonRpcConfiguration().getAuthenticationAlgorithm() != null) {
        params.add("--rpc-http-authentication-jwt-algorithm");
        params.add(node.jsonRpcConfiguration().getAuthenticationAlgorithm().toString());
      }
    }

    if (node.isEngineRpcEnabled()) {
      params.add("--engine-rpc-port");
      params.add(node.jsonEngineListenPort().get().toString());

      if (node.isEngineAuthDisabled()) {
        params.add("--engine-jwt-disabled");
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
      if (!node.webSocketConfiguration().getRpcApisNoAuth().isEmpty()) {
        params.add("--rpc-ws-api-methods-no-auth");
        params.add(apiList(node.webSocketConfiguration().getRpcApisNoAuth()));
      }
      if (node.webSocketConfiguration().isAuthenticationEnabled()) {
        params.add("--rpc-ws-authentication-enabled");
      }
      if (node.webSocketConfiguration().getAuthenticationCredentialsFile() != null) {
        params.add("--rpc-ws-authentication-credentials-file");
        params.add(node.webSocketConfiguration().getAuthenticationCredentialsFile());
      }
      if (node.webSocketConfiguration().getAuthenticationPublicKeyFile() != null) {
        params.add("--rpc-ws-authentication-jwt-public-key-file");
        params.add(
            node.webSocketConfiguration().getAuthenticationPublicKeyFile().getAbsolutePath());
      }
      if (node.webSocketConfiguration().getAuthenticationAlgorithm() != null) {
        params.add("--rpc-ws-authentication-jwt-algorithm");
        params.add(node.webSocketConfiguration().getAuthenticationAlgorithm().toString());
      }
    }

    if (node.isJsonRpcIpcEnabled()) {
      final JsonRpcIpcConfiguration ipcConfiguration = node.jsonRpcIpcConfiguration();
      params.add("--Xrpc-ipc-enabled");
      params.add("--Xrpc-ipc-path");
      params.add(ipcConfiguration.getPath().toString());
      params.add("--Xrpc-ipc-apis");
      params.add(String.join(",", ipcConfiguration.getEnabledApis()));
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
      if (node.isMetricsEnabled() || metricsConfiguration.isPushEnabled()) {
        params.add("--metrics-protocol");
        params.add(metricsConfiguration.getProtocol().name());
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
      if (node.getTLSConfiguration().isPresent()) {
        final TLSConfiguration config = node.getTLSConfiguration().get();
        params.add("--Xp2p-tls-enabled");
        params.add("--Xp2p-tls-keystore-type");
        params.add(config.getKeyStoreType());
        params.add("--Xp2p-tls-keystore-file");
        params.add(config.getKeyStorePath().toAbsolutePath().toString());
        params.add("--Xp2p-tls-keystore-password-file");
        params.add(config.getKeyStorePasswordPath().toAbsolutePath().toString());
        params.add("--Xp2p-tls-crl-file");
        params.add(config.getCrlPath().toAbsolutePath().toString());
        if (null != config.getTrustStoreType()) {
          params.add("--Xp2p-tls-truststore-type");
          params.add(config.getTrustStoreType());
          params.add("--Xp2p-tls-truststore-file");
          params.add(config.getTrustStorePath().toAbsolutePath().toString());
          params.add("--Xp2p-tls-truststore-password-file");
          params.add(config.getTrustStorePasswordPath().toAbsolutePath().toString());
        }
      }
    }

    if (node.isRevertReasonEnabled()) {
      params.add("--revert-reason-enabled");
    }

    params.add("--Xsecp256k1-native-enabled=" + node.isSecp256k1Native());
    params.add("--Xaltbn128-native-enabled=" + node.isAltbn128Native());

    node.getPermissioningConfiguration()
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(
            permissioningConfiguration -> {
              if (permissioningConfiguration.isNodeAllowlistEnabled()) {
                params.add("--permissions-nodes-config-file-enabled");
              }
              if (permissioningConfiguration.getNodePermissioningConfigFilePath() != null) {
                params.add("--permissions-nodes-config-file");
                params.add(permissioningConfiguration.getNodePermissioningConfigFilePath());
              }
              if (permissioningConfiguration.isAccountAllowlistEnabled()) {
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
              if (permissioningConfiguration.isSmartContractNodeAllowlistEnabled()) {
                params.add("--permissions-nodes-contract-enabled");
              }
              if (permissioningConfiguration.getNodeSmartContractAddress() != null) {
                params.add("--permissions-nodes-contract-address");
                params.add(permissioningConfiguration.getNodeSmartContractAddress().toString());
              }
              if (permissioningConfiguration.isSmartContractAccountAllowlistEnabled()) {
                params.add("--permissions-accounts-contract-enabled");
              }
              if (permissioningConfiguration.getAccountSmartContractAddress() != null) {
                params.add("--permissions-accounts-contract-address");
                params.add(permissioningConfiguration.getAccountSmartContractAddress().toString());
              }
              params.add("--permissions-nodes-contract-version");
              params.add(
                  String.valueOf(
                      permissioningConfiguration.getNodeSmartContractInterfaceVersion()));
            });

    node.getPkiKeyStoreConfiguration()
        .ifPresent(
            pkiConfig -> {
              params.add("--Xpki-block-creation-enabled");

              params.add("--Xpki-block-creation-keystore-certificate-alias");
              params.add(pkiConfig.getCertificateAlias());

              params.add("--Xpki-block-creation-keystore-type");
              params.add(pkiConfig.getKeyStoreType());

              params.add("--Xpki-block-creation-keystore-file");
              params.add(pkiConfig.getKeyStorePath().toAbsolutePath().toString());

              params.add("--Xpki-block-creation-keystore-password-file");
              params.add(pkiConfig.getKeyStorePasswordPath().toAbsolutePath().toString());

              params.add("--Xpki-block-creation-truststore-type");
              params.add(pkiConfig.getTrustStoreType());

              params.add("--Xpki-block-creation-truststore-file");
              params.add(pkiConfig.getTrustStorePath().toAbsolutePath().toString());

              params.add("--Xpki-block-creation-truststore-password-file");
              params.add(pkiConfig.getTrustStorePasswordPath().toAbsolutePath().toString());
            });

    params.addAll(node.getExtraCLIOptions());

    params.add("--key-value-storage");
    params.add("rocksdb");

    params.add("--auto-log-bloom-caching-enabled");
    params.add("false");

    params.add("--strict-tx-replay-protection-enabled");
    params.add(Boolean.toString(node.isStrictTxReplayProtectionEnabled()));

    final String level = System.getProperty("root.log.level");
    if (level != null) {
      params.add("--logging=" + level);
    }

    params.addAll(node.getRunCommand());

    LOG.info("Creating besu process with params {}", params);
    final ProcessBuilder processBuilder =
        new ProcessBuilder(params)
            .directory(new File(System.getProperty("user.dir")).getParentFile().getParentFile())
            .redirectErrorStream(true)
            .redirectInput(Redirect.INHERIT);
    if (!node.getPlugins().isEmpty()) {
      processBuilder
          .environment()
          .put(
              "BESU_OPTS",
              "-Dbesu.plugins.dir=" + dataDir.resolve("plugins").toAbsolutePath().toString());
    }
    // Use non-blocking randomness for acceptance tests
    processBuilder
        .environment()
        .put(
            "JAVA_OPTS",
            "-Djava.security.properties="
                + "acceptance-tests/tests/build/resources/test/acceptanceTesting.security");
    try {
      checkState(
          isNotAliveOrphan(node.getName()),
          "A live process with name: %s, already exists. Cannot create another with the same name as it would orphan the first",
          node.getName());

      final Process process = processBuilder.start();
      process.onExit().thenRun(() -> node.setExitCode(process.exitValue()));
      outputProcessorExecutor.execute(() -> printOutput(node, process));
      besuProcesses.put(node.getName(), process);
    } catch (final IOException e) {
      LOG.error("Error starting BesuNode process", e);
    }

    if (node.getRunCommand().isEmpty()) {
      waitForFile(dataDir, "besu.ports");
      waitForFile(dataDir, "besu.networks");
    }
    MDC.remove("node");
  }

  private boolean isNotAliveOrphan(final String name) {
    final Process orphan = besuProcesses.get(name);
    return orphan == null || !orphan.isAlive();
  }

  private void printOutput(final BesuNode node, final Process process) {
    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {

      MDC.put("node", node.getName());

      String line = in.readLine();
      while (line != null) {
        // would be nice to pass up the log level of the incoming log line
        PROCESS_LOG.info(line);
        if (capturingConsole) {
          consoleOut.println(line);
        }
        line = in.readLine();
      }
    } catch (final IOException e) {
      if (besuProcesses.containsKey(node.getName())) {
        LOG.error("Failed to read output from process for node " + node.getName(), e);
      } else {
        LOG.debug("Stdout from process {} closed", node.getName());
      }
    }
  }

  private Path createGenesisFile(final BesuNode node, final String genesisConfig) {
    try {
      final Path genesisFile = Files.createTempFile(node.homeDirectory(), "genesis", "");
      genesisFile.toFile().deleteOnExit();
      Files.write(genesisFile, genesisConfig.getBytes(UTF_8));
      return genesisFile;
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void createStaticNodes(final BesuNode node) {
    StaticNodesUtils.createStaticNodesFile(node.homeDirectory(), node.getStaticNodes());
  }

  private String apiList(final Collection<String> rpcApis) {
    return String.join(",", rpcApis);
  }

  @Override
  public void stopNode(final BesuNode node) {
    node.stop();
    if (besuProcesses.containsKey(node.getName())) {
      killBesuProcess(node.getName());
    } else {
      LOG.error("There was a request to stop an unknown node: {}", node.getName());
    }
  }

  @Override
  public synchronized void shutdown() {
    final Set<String> localMap = new HashSet<>(besuProcesses.keySet());
    localMap.forEach(this::killBesuProcess);
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
    final Process process = besuProcesses.get(nodeName);
    return process != null && process.isAlive();
  }

  private void killBesuProcess(final String name) {
    final Process process = besuProcesses.remove(name);
    if (process == null) {
      LOG.error("Process {} wasn't in our list", name);
      return;
    }
    if (!process.isAlive()) {
      LOG.info("Process {} already exited, pid {}", name, process.pid());
      return;
    }

    LOG.info("Killing {} process, pid {}", name, process.pid());

    process.destroy();
    try {
      process.waitFor(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      LOG.warn("Wait for death of process {} was interrupted", name, e);
    }

    if (process.isAlive()) {
      LOG.warn("Process {} still alive, destroying forcibly now, pid {}", name, process.pid());
      try {
        process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
      } catch (final Exception e) {
        // just die already
      }
      LOG.info("Process exited with code {}", process.exitValue());
    }
  }

  @Override
  public void startConsoleCapture() {
    consoleContents.reset();
    capturingConsole = true;
  }

  @Override
  public String getConsoleContents() {
    capturingConsole = false;
    return consoleContents.toString(UTF_8);
  }
}
