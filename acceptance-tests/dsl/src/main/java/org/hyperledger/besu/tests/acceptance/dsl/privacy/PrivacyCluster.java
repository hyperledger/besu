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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNodeRunner;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.enclave.testutil.EnclaveType;
import org.hyperledger.enclave.testutil.TesseraTestHarness;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivacyCluster {
  private static final Logger LOG = LoggerFactory.getLogger(PrivacyCluster.class);
  private final NetConditions net;
  private final BesuNodeRunner besuNodeRunner;

  private List<PrivacyNode> nodes = emptyList();
  private List<RunnableNode> runnableNodes = emptyList();
  private PrivacyNode bootNode;

  public PrivacyCluster(final NetConditions net) {
    this.net = net;
    this.besuNodeRunner = BesuNodeRunner.instance();
  }

  public void start(final PrivacyNode... nodes) {
    start(Arrays.asList(nodes));
  }

  public void start(final List<PrivacyNode> nodes) {
    startNodes(nodes);
    awaitPeerCount(nodes);
  }

  public void startNodes(final PrivacyNode... nodes) {
    startNodes(Arrays.asList(nodes));
  }

  public void startNodes(final List<PrivacyNode> nodes) {
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Can't start a cluster with no nodes");
    }
    this.nodes = nodes;
    this.runnableNodes = nodes.stream().map(PrivacyNode::getBesu).collect(Collectors.toList());

    final Optional<PrivacyNode> bootNode = selectAndStartBootnode(nodes);

    nodes.stream()
        .filter(node -> bootNode.map(boot -> boot != node).orElse(true))
        .forEach(this::startNode);
  }

  public void awaitPeerCount(final PrivacyNode... nodes) {
    awaitPeerCount(Arrays.asList(nodes));
  }

  public void awaitPeerCount(final List<PrivacyNode> nodes) {
    for (final PrivacyNode node : nodes) {
      LOG.info("Awaiting peer discovery for node {}", node.getName());
      node.awaitPeerDiscovery(net.awaitPeerCount(nodes.size() - 1));
    }

    verifyAllEnclaveNetworkConnections();
  }

  public List<PrivacyNode> getNodes() {
    return nodes;
  }

  /** Verify that each Enclave has connected to every other Enclave */
  public void verifyAllEnclaveNetworkConnections() {
    nodes.forEach(
        privacyNode -> {
          final List<PrivacyNode> otherNodes =
              nodes.stream()
                  .filter(not(privacyNode::equals))
                  .collect(Collectors.toUnmodifiableList());
          privacyNode.testEnclaveConnection(otherNodes);
        });
  }

  public void stop() {
    for (final PrivacyNode node : nodes) {
      besuNodeRunner.stopNode(node.getBesu());
    }
  }

  public void stopNode(final PrivacyNode node) {
    node.getEnclave().stop();
    besuNodeRunner.stopNode(node.getBesu());
  }

  public void close() {
    stop();
    for (final PrivacyNode node : nodes) {
      node.close();
    }
    besuNodeRunner.shutdown();
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
      if (bootNode.getEnclave().getEnclaveType() == EnclaveType.TESSERA) {
        final URI otherNode = bootNode.getEnclave().nodeUrl();
        try {
          // Substitute IP with hostname for test container network
          final URI otherNodeHostname =
              new URI(
                  otherNode.getScheme()
                      + "://"
                      + bootNode.getName()
                      + ":"
                      + TesseraTestHarness.p2pPort);
          node.addOtherEnclaveNode(otherNodeHostname);
        } catch (Exception ex) {
          throw new RuntimeException("Invalid node URI");
        }
      } else {
        node.addOtherEnclaveNode(bootNode.getEnclave().nodeUrl());
      }
    }

    LOG.info(
        "Starting node {} (id = {}...{})",
        node.getName(),
        node.getNodeId().substring(0, 4),
        node.getNodeId().substring(124));
    node.start(besuNodeRunner);
  }
}
