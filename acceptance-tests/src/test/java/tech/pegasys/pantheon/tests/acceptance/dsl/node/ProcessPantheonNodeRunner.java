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

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

public class ProcessPantheonNodeRunner implements PantheonNodeRunner {

  private final Logger LOG = LogManager.getLogger();

  private final Map<String, Process> pantheonProcesses = new HashMap<>();

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
    params.add(Integer.toString(node.p2pListenPort()));

    if (node.getMiningParameters().isMiningEnabled()) {
      params.add("--miner-enabled");
      params.add("--miner-coinbase");
      params.add(node.getMiningParameters().getCoinbase().get().toString());
    }

    if (node.getPrivacyParameters().isEnabled()) {
      params.add("--privacy-enabled");
      params.add("--privacy-url");
      params.add(node.getPrivacyParameters().getUrl());
      params.add("--privacy-public-key-file");
      params.add(node.getPrivacyParameters().getPublicKeyFile().getAbsolutePath());
      params.add("--privacy-precompiled-address");
      params.add(String.valueOf(node.getPrivacyParameters().getPrivacyAddress()));
    }

    params.add("--bootnodes");

    if (!node.bootnodes().isEmpty()) {
      params.add(node.bootnodes().stream().map(URI::toString).collect(Collectors.joining(",")));
    }

    if (node.jsonRpcEnabled()) {
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

    if (node.ethNetworkConfig().isPresent()) {
      final EthNetworkConfig ethNetworkConfig = node.ethNetworkConfig().get();
      final Path genesisFile = createGenesisFile(node, ethNetworkConfig);
      params.add("--genesis-file");
      params.add(genesisFile.toString());
      params.add("--network-id");
      params.add(Integer.toString(ethNetworkConfig.getNetworkId()));
    }

    if (!node.p2pEnabled()) {
      params.add("--p2p-enabled");
      params.add("false");
    }

    node.getPermissioningConfiguration()
        .ifPresent(
            permissioningConfiguration -> {
              if (permissioningConfiguration.isNodeWhitelistEnabled()) {
                params.add("--permissions-nodes-enabled");
              }
              if (permissioningConfiguration.isAccountWhitelistEnabled()) {
                params.add("--permissions-accounts-enabled");
              }
              if (permissioningConfiguration.getConfigurationFilePath() != null) {
                params.add("--permissions-config-file");
                params.add(permissioningConfiguration.getConfigurationFilePath());
              }
            });

    LOG.info("Creating pantheon process with params {}", params);
    final ProcessBuilder processBuilder =
        new ProcessBuilder(params)
            .directory(new File(System.getProperty("user.dir")).getParentFile())
            .inheritIO();

    try {
      final Process process = processBuilder.start();
      pantheonProcesses.put(node.getName(), process);
    } catch (final IOException e) {
      LOG.error("Error starting PantheonNode process", e);
    }

    waitForPortsFile(dataDir);
  }

  private Path createGenesisFile(final PantheonNode node, final EthNetworkConfig ethNetworkConfig) {
    try {
      final Path genesisFile = Files.createTempFile(node.homeDirectory(), "genesis", "");
      genesisFile.toFile().deleteOnExit();
      Files.write(genesisFile, ethNetworkConfig.getGenesisConfig().getBytes(UTF_8));
      return genesisFile;
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
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
