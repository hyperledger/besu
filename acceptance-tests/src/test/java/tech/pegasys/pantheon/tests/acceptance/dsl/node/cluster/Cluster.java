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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Net;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeRunner;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.waitcondition.WaitCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Cluster implements AutoCloseable {
  public static final int NETWORK_ID = 10;

  private final Map<String, RunnableNode> nodes = new HashMap<>();
  private final PantheonNodeRunner pantheonNodeRunner = PantheonNodeRunner.instance();
  private final Net net;
  private final ClusterConfiguration clusterConfiguration;

  public Cluster(final Net net) {
    this(new ClusterConfigurationBuilder().build(), net);
  }

  public Cluster(final ClusterConfiguration clusterConfiguration, final Net net) {
    this.clusterConfiguration = clusterConfiguration;
    this.net = net;
  }

  public void start(final Node... nodes) {
    start(
        Arrays.stream(nodes)
            .map(
                n -> {
                  assertThat(n instanceof RunnableNode).isTrue();
                  return (RunnableNode) n;
                })
            .collect(Collectors.toList()));
  }

  public void start(final List<? extends RunnableNode> nodes) {
    this.nodes.clear();

    final List<String> bootNodes = new ArrayList<>();

    for (final RunnableNode node : nodes) {
      this.nodes.put(node.getName(), node);
      if (node.getConfiguration().isBootnode()) {
        bootNodes.add(node.getConfiguration().enodeUrl());
      }
    }

    for (final RunnableNode node : nodes) {
      if (node.getConfiguration().bootnodes().isEmpty()) {
        node.getConfiguration().bootnodes(bootNodes);
      }
      Optional<EthNetworkConfig> ethNetworkConfig =
          node.getConfiguration()
              .genesisConfigProvider()
              .createGenesisConfig(nodes)
              .map(config -> new EthNetworkConfig(config, NETWORK_ID, bootNodes));
      node.getConfiguration().ethNetworkConfig(ethNetworkConfig);
      node.start(pantheonNodeRunner);
    }

    if (clusterConfiguration.isAwaitPeerDiscovery()) {
      for (final RunnableNode node : nodes) {
        node.awaitPeerDiscovery(net.awaitPeerCount(nodes.size() - 1));
      }
    }
  }

  public void stop() {
    for (final RunnableNode node : nodes.values()) {
      node.stop();
    }
    pantheonNodeRunner.shutdown();
  }

  public void stopNode(final PantheonNode node) {
    pantheonNodeRunner.stopNode(node);
  }

  @Override
  public void close() {
    for (final RunnableNode node : nodes.values()) {
      node.close();
    }
    pantheonNodeRunner.shutdown();
  }

  public void verify(final Condition expected) {
    for (final Node node : nodes.values()) {
      expected.verify(node);
    }
  }

  public void verifyOnActiveNodes(final Condition condition) {
    nodes.values().stream()
        .filter(node -> pantheonNodeRunner.isActive(node.getName()))
        .forEach(condition::verify);
  }

  public void waitUntil(final WaitCondition condition) {
    for (final Node node : nodes.values()) {
      node.waitUntil(condition);
    }
  }
}
