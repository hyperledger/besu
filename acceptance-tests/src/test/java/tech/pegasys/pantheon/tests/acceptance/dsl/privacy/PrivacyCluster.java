/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.net.NetConditions;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeRunner;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyCluster {
  private static final Logger LOG = LogManager.getLogger();
  private final NetConditions net;
  private final PantheonNodeRunner pantheonNodeRunner;

  private List<PrivacyNode> nodes = emptyList();
  private List<RunnableNode> runnableNodes = emptyList();
  private PrivacyNode bootNode;

  public PrivacyCluster(final NetConditions net) {
    this.net = net;
    this.pantheonNodeRunner = PantheonNodeRunner.instance();
  }

  public void start(final PrivacyNode... nodes) {
    start(Arrays.asList(nodes));
  }

  public void start(final List<PrivacyNode> nodes) {
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Can't start a cluster with no nodes");
    }
    this.nodes = nodes;
    this.runnableNodes = nodes.stream().map(n -> n.getPantheon()).collect(Collectors.toList());

    final Optional<PrivacyNode> bootNode = selectAndStartBootnode(nodes);

    nodes.stream()
        .filter(node -> bootNode.map(boot -> boot != node).orElse(true))
        .forEach(this::startNode);

    for (final PrivacyNode node : nodes) {
      LOG.info("Awaiting peer discovery for node {}", node.getName());
      node.awaitPeerDiscovery(net.awaitPeerCount(nodes.size() - 1));
    }

    verifyAllOrionNetworkConnections();
  }

  public List<PrivacyNode> getNodes() {
    return nodes;
  }

  /** Verify that each Orion node has connected to every other Orion */
  public void verifyAllOrionNetworkConnections() {
    for (int i = 0; i < nodes.size() - 1; i++) {
      nodes.get(i).testOrionConnection(nodes.subList(i + 1, nodes.size()));
    }
  }

  public void stop() {
    for (final PrivacyNode node : nodes) {
      pantheonNodeRunner.stopNode(node.getPantheon());
    }
  }

  public void stopNode(final PrivacyNode node) {
    node.getOrion().stop();
    pantheonNodeRunner.stopNode(node.getPantheon());
  }

  public void close() {
    stop();
    for (final PrivacyNode node : nodes) {
      node.close();
    }
    pantheonNodeRunner.shutdown();
  }

  private Optional<PrivacyNode> selectAndStartBootnode(final List<PrivacyNode> nodes) {
    final Optional<PrivacyNode> bootNode =
        nodes.stream()
            .filter(node -> node.getConfiguration().isBootnodeEligible())
            .filter(node -> node.getConfiguration().isP2pEnabled())
            .filter(node -> node.getConfiguration().isDiscoveryEnabled())
            .findFirst();

    bootNode.ifPresent(
        b -> {
          LOG.info("Selected node {} as bootnode", b.getName());
          startNode(b, true);
          this.bootNode = b;
        });

    return bootNode;
  }

  private void startNode(final PrivacyNode node) {
    startNode(node, false);
  }

  private void startNode(final PrivacyNode node, final boolean isBootNode) {
    node.getConfiguration()
        .setBootnodes(isBootNode ? emptyList() : Collections.singletonList(bootNode.enodeUrl()));

    node.getConfiguration()
        .getGenesisConfigProvider()
        .create(runnableNodes)
        .ifPresent(node.getConfiguration()::setGenesisConfig);

    if (!isBootNode) {
      node.addOtherEnclaveNode(bootNode.getOrion().nodeUrl());
    }

    LOG.info(
        "Starting node {} (id = {}...{})",
        node.getName(),
        node.getNodeId().substring(0, 4),
        node.getNodeId().substring(124));
    node.start(pantheonNodeRunner);
  }
}
