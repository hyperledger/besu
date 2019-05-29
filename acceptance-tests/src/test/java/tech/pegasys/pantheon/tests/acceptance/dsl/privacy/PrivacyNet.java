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

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy.PrivacyPantheonNodeFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.rules.TemporaryFolder;

public class PrivacyNet {
  private static final Logger LOG = LogManager.getLogger();

  private static final String PANTHEON_KEYPAIR_NODE_1 = "key";
  private static final String PANTHEON_KEYPAIR_NODE_2 = "key1";
  private static final String PANTHEON_KEYPAIR_NODE_3 = "key2";

  private static final Map<String, String> KNOWN_PANTHEON_KEYPAIRS = new HashMap<>();

  private final TemporaryFolder temporaryFolder;
  private Cluster cluster;

  private Map<String, PrivacyNode> nodes;

  static {
    KNOWN_PANTHEON_KEYPAIRS.put("Alice", PANTHEON_KEYPAIR_NODE_1);
    KNOWN_PANTHEON_KEYPAIRS.put("Bob", PANTHEON_KEYPAIR_NODE_2);
    KNOWN_PANTHEON_KEYPAIRS.put("Charlie", PANTHEON_KEYPAIR_NODE_3);
  }

  private PrivacyNet(
      final TemporaryFolder temporaryFolder,
      final Map<String, PrivacyNode> privacyNodes,
      final Cluster cluster) {
    this.temporaryFolder = temporaryFolder;
    this.nodes = privacyNodes;
    this.cluster = cluster;
  }

  public static PrivacyNet.Builder builder(
      final TemporaryFolder temporaryFolder,
      final PrivacyPantheonNodeFactory pantheonNodeFactory,
      final Cluster cluster,
      final boolean ibft) {
    return new Builder(temporaryFolder, pantheonNodeFactory, cluster, ibft);
  }

  public Map<String, PrivacyNode> getNodes() {
    return nodes;
  }

  public PantheonNode getPantheon(final String name) {
    return nodes.get(name);
  }

  public OrionTestHarness getEnclave(final String name) {
    return nodes.get(name).orion;
  }

  public void startPrivacyNet() {
    if (nodes == null)
      throw new IllegalStateException(
          "Cannot start network nodes.  init method was never called to initialize the nodes");
    cluster.start(nodes.values().toArray(new PrivacyNode[0]));
    verifyAllOrionNetworkConnections();
  }

  public void stopPrivacyNet() {
    try {
      cluster.stop();
    } catch (RuntimeException e) {
      LOG.error("Error stopping Pantheon nodes.  Logging and continuing.", e);
    }
    try {
      stopOrionNodes();
    } catch (RuntimeException e) {
      LOG.error("Error stopping Orion nodes.  Logging and continuing.", e);
    }
  }

  private void stopOrionNodes() {
    if (nodes == null) return; // Never started
    for (PrivacyNode node : nodes.values()) {
      try {
        node.orion.getOrion().stop();
      } catch (RuntimeException e) {
        LOG.error(
            String.format(
                "Error stopping Orion node %s.  Logging and continuing to shutdown other nodes.",
                node.orion.nodeUrl()),
            e);
      }
    }
  }

  /** Verify that each Orion node has connected to every other Orion */
  public void verifyAllOrionNetworkConnections() {
    PrivacyNode[] nodeList = nodes.values().toArray(new PrivacyNode[0]);
    for (int i = 0; i < nodeList.length; i++) {
      for (int j = i + 1; j < nodeList.length; j++) {
        nodeList[i].testOrionConnection(nodeList[j]);
      }
      for (int j = i + 2; j < nodeList.length; j = j + 2) {
        nodeList[i].testOrionConnection(nodeList[j]);
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("temporaryFolder      = %s\n", temporaryFolder.getRoot()));
    for (PrivacyNode privacyNode : nodes.values()) {
      sb.append(String.format("Pantheon Node Name   = %s\n", privacyNode.getName()));
      sb.append(String.format("Pantheon Address     = %s\n", privacyNode.getAddress()));
      sb.append(
          String.format("Pantheon Private Key = %s\n", privacyNode.keyPair().getPrivateKey()));
      sb.append(String.format("Pantheon Public  Key = %s\n", privacyNode.keyPair().getPublicKey()));
      sb.append(String.format("Orion Pub Key        = %s\n", privacyNode.getOrionPubKeyBytes()));
      sb.append(
          String.format(
              "Orion Pub Key Base64 = %s\n",
              Base64.getEncoder()
                  .encodeToString(privacyNode.getOrionPubKeyBytes().extractArray())));

      sb.append(String.format("Pantheon             = %s\n", privacyNode));
      sb.append(String.format("Orion Config         = %s\n", privacyNode.orion.getConfig()));
      sb.append(String.format("Orion Pub Key        = %s\n", privacyNode.getOrionPubKeyBytes()));
    }
    return sb.toString();
  }

  public static class Builder {
    private TemporaryFolder temporaryFolder;
    private PrivacyPantheonNodeFactory pantheonNodeFactory;
    private Cluster cluster;
    private final boolean ibft;

    private String otherOrionNode = null;

    private Map<String, PrivacyNode> nodes;

    private Builder(
        final TemporaryFolder temporaryFolder,
        final PrivacyPantheonNodeFactory pantheonNodeFactory,
        final Cluster cluster,
        final boolean ibft) {
      this.temporaryFolder = temporaryFolder;
      this.pantheonNodeFactory = pantheonNodeFactory;
      this.cluster = cluster;
      this.ibft = ibft;
    }

    public Builder addMinerNode(final String name) throws IOException {
      return addNode(name, true);
    }

    public Builder addNode(final String name) throws IOException {
      return addNode(name, false);
    }

    public Builder addNode(final String name, final Optional<String> keyPath) throws IOException {
      return addNode(name, false, keyPath);
    }

    public Builder addNode(final String name, final boolean isMiningEnabled) throws IOException {
      return addNode(name, isMiningEnabled, Optional.empty());
    }

    public Builder addNode(
        final String name, final boolean isMiningEnabled, final Optional<String> keyPath)
        throws IOException {
      final PrivacyNode node = makeNode(name, isMiningEnabled, otherOrionNode, keyPath);
      if (nodes == null) {
        nodes = new HashMap<>();
        otherOrionNode = node.orion.nodeUrl(); // All nodes use first added node for discovery
      }
      nodes.put(name, node);
      return this;
    }

    public PrivacyNode makeNode(
        final String name,
        final boolean isMiningEnabled,
        final String otherOrionNodes,
        final Optional<String> orionKeyPath)
        throws IOException {

      final OrionTestHarness orion;
      if (otherOrionNodes == null) {
        // Need conditional because createEnclave will choke if passing in null
        orion = createEnclave(temporaryFolder, orionKeyPath);
      } else {
        orion = createEnclave(temporaryFolder, orionKeyPath, otherOrionNodes);
      }

      final PrivacyNode node;
      final String keyFilePath = KNOWN_PANTHEON_KEYPAIRS.get(name);
      if (isMiningEnabled && !ibft) {
        node =
            pantheonNodeFactory.createPrivateTransactionEnabledMinerNode(
                name, generatePrivacyParameters(orion), keyFilePath, orion);
      } else if (!isMiningEnabled && !ibft) {
        node =
            pantheonNodeFactory.createPrivateTransactionEnabledNode(
                name, generatePrivacyParameters(orion), keyFilePath, orion);
      } else {
        node =
            pantheonNodeFactory.createIbft2NodePrivacyEnabled(
                name, generatePrivacyParameters(orion), keyFilePath, orion);
      }

      return node;
    }

    protected OrionTestHarness createEnclave(
        final TemporaryFolder temporaryFolder,
        final Optional<String> pubKeyPath,
        final String... othernode)
        throws IOException {
      final Path tmpPath = temporaryFolder.newFolder().toPath();
      final String orionPublicKeyFileName = pubKeyPath.orElse(provideNextKnownOrionKey());
      final String orionPrivateKeyFileName = privaKeyPathFromPubKeyPath(orionPublicKeyFileName);
      return OrionTestHarnessFactory.create(
          tmpPath, orionPublicKeyFileName, orionPrivateKeyFileName, othernode);
    }

    private String privaKeyPathFromPubKeyPath(final String orionPublicKeyFileName) {
      return orionPublicKeyFileName.substring(0, orionPublicKeyFileName.length() - 3) + "key";
    }

    private Integer nextKnownOrionKey = 0;

    private String provideNextKnownOrionKey() {
      if (nextKnownOrionKey < 4) {
        return String.format("orion_key_%d.pub", nextKnownOrionKey++);
      }
      throw new RuntimeException("Limit of known nodes reached");
    }

    private PrivacyParameters generatePrivacyParameters(final OrionTestHarness testHarness)
        throws IOException {
      return new PrivacyParameters.Builder()
          .setEnabled(true)
          .setEnclaveUrl(testHarness.clientUrl())
          .setEnclavePublicKeyUsingFile(testHarness.getConfig().publicKeys().get(0).toFile())
          .setDataDir(temporaryFolder.newFolder().toPath())
          .build();
    }

    public PrivacyNet build() {
      Preconditions.checkNotNull(nodes);
      return new PrivacyNet(temporaryFolder, nodes, cluster);
    }
  }
}
