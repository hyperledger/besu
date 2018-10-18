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

import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;

import java.io.File;
import java.io.IOException;
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
    params.add("--datadir");
    params.add(dataDir.toAbsolutePath().toString());

    params.add("--dev-mode");

    params.add("--p2p-listen");
    params.add(node.p2pListenAddress());

    if (node.getMiningParameters().isMiningEnabled()) {
      params.add("--miner-enabled");
      params.add("--miner-coinbase");
      params.add(node.getMiningParameters().getCoinbase().get().toString());
    }

    params.add("--bootnodes");
    params.add(String.join(",", node.bootnodes()));

    if (node.jsonRpcEnabled()) {
      params.add("--rpc-enabled");
      params.add("--rpc-listen");
      params.add(node.jsonRpcListenAddress().get());
      params.add("--rpc-api");
      params.add(apiList(node.jsonRpcConfiguration().getRpcApis()));
    }

    if (node.wsRpcEnabled()) {
      params.add("--ws-enabled");
      params.add("--ws-listen");
      params.add(node.wsRpcListenAddress().get());
      params.add("--ws-api");
      params.add(apiList(node.webSocketConfiguration().getRpcApis()));
    }

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

  private String apiList(final Collection<RpcApi> rpcApis) {
    return String.join(",", rpcApis.stream().map(RpcApis::getValue).collect(Collectors.toList()));
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

  private void killPantheonProcess(final String name, final Process process) {
    LOG.info("Killing " + name + " process");

    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (process.isAlive()) {
                process.destroy();
                return false;
              } else {
                pantheonProcesses.remove(name);
                return true;
              }
            });
  }
}
