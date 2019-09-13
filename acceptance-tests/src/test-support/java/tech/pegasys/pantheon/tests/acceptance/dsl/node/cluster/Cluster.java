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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.net.NetConditions;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeRunner;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Cluster implements AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<String, RunnableNode> nodes = new HashMap<>();
  private final PantheonNodeRunner pantheonNodeRunner;
  private final NetConditions net;
  private final ClusterConfiguration clusterConfiguration;
  private List<? extends RunnableNode> originalNodes = emptyList();
  private List<URI> bootnodes = new ArrayList<>();

  public Cluster(final NetConditions net) {
    this(new ClusterConfigurationBuilder().build(), net, PantheonNodeRunner.instance());
  }

  public Cluster(final ClusterConfiguration clusterConfiguration, final NetConditions net) {
    this(clusterConfiguration, net, PantheonNodeRunner.instance());
  }

  public Cluster(
      final ClusterConfiguration clusterConfiguration,
      final NetConditions net,
      final PantheonNodeRunner pantheonNodeRunner) {
    this.clusterConfiguration = clusterConfiguration;
    this.net = net;
    this.pantheonNodeRunner = pantheonNodeRunner;
  }

  public void start(final Node... nodes) {
    start(
        Arrays.stream(nodes)
            .map(
                n -> {
                  assertThat(n instanceof RunnableNode).isTrue();
                  return (RunnableNode) n;
                })
            .collect(toList()));
  }

  public void start(final List<? extends RunnableNode> nodes) {
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Can't start a cluster with no nodes");
    }
    this.originalNodes = nodes;
    this.nodes.clear();
    this.bootnodes.clear();
    nodes.forEach(node -> this.nodes.put(node.getName(), node));

    final Optional<? extends RunnableNode> bootnode = selectAndStartBootnode(nodes);

    nodes.stream()
        .filter(node -> bootnode.map(boot -> boot != node).orElse(true))
        .forEach(this::startNode);

    if (clusterConfiguration.isAwaitPeerDiscovery()) {
      for (final RunnableNode node : nodes) {
        LOG.info("Awaiting peer discovery for node {}", node.getName());
        node.awaitPeerDiscovery(net.awaitPeerCount(nodes.size() - 1));
      }
    }
  }

  private Optional<? extends RunnableNode> selectAndStartBootnode(
      final List<? extends RunnableNode> nodes) {
    final Optional<? extends RunnableNode> bootnode =
        nodes.stream()
            .filter(node -> node.getConfiguration().isBootnodeEligible())
            .filter(node -> node.getConfiguration().isP2pEnabled())
            .filter(node -> node.getConfiguration().isDiscoveryEnabled())
            .findFirst();

    bootnode.ifPresent(
        b -> {
          LOG.info("Selected node {} as bootnode", b.getName());
          startNode(b, true);
          bootnodes.add(b.enodeUrl());
        });

    return bootnode;
  }

  public void addNode(final Node node) {
    assertThat(node).isInstanceOf(RunnableNode.class);
    final RunnableNode runnableNode = (RunnableNode) node;

    nodes.put(runnableNode.getName(), runnableNode);
    startNode(runnableNode);

    if (clusterConfiguration.isAwaitPeerDiscovery()) {
      runnableNode.awaitPeerDiscovery(net.awaitPeerCount(nodes.size() - 1));
    }
  }

  private void startNode(final RunnableNode node) {
    this.startNode(node, false);
  }

  private void startNode(final RunnableNode node, final boolean isBootNode) {
    node.getConfiguration().setBootnodes(isBootNode ? emptyList() : bootnodes);

    node.getConfiguration()
        .getGenesisConfigProvider()
        .create(originalNodes)
        .ifPresent(node.getConfiguration()::setGenesisConfig);
    LOG.info(
        "Starting node {} (id = {}...{})",
        node.getName(),
        node.getNodeId().substring(0, 4),
        node.getNodeId().substring(124));
    node.start(pantheonNodeRunner);
  }

  public void stop() {
    // stops nodes but do not shutdown pantheonNodeRunner
    for (final RunnableNode node : nodes.values()) {
      if (node instanceof PantheonNode) {
        pantheonNodeRunner.stopNode(
            (PantheonNode) node); // pantheonNodeRunner.stopNode also calls node.stop
      } else {
        node.stop();
      }
    }
  }

  public void stopNode(final PantheonNode node) {
    pantheonNodeRunner.stopNode(node);
  }

  @Override
  public void close() {
    stop();
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
}
