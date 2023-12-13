/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.config;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;

import java.net.ServerSocket;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BootNodesGenesisSetupTest extends AcceptanceTestBase {
  private static final String CURVE_NAME = "secp256k1";
  private static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;

  private static ECDomainParameters curve;

  private Node nodeA;
  private Node nodeB;

  @BeforeAll
  public static void environment() {
    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  @BeforeEach
  public void setUp() throws Exception {
    int nodeAP2pBindingPort;
    int nodeBP2pBindingPort;
    try (ServerSocket nodeASocket = new ServerSocket(0);
        ServerSocket nodeBSocket = new ServerSocket(0)) {
      nodeAP2pBindingPort = nodeASocket.getLocalPort();
      nodeBP2pBindingPort = nodeBSocket.getLocalPort();
    }
    final KeyPair nodeAKeyPair =
        createKeyPair(
            Bytes32.fromHexString(
                "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"));
    final KeyPair nodeBKeyPair =
        createKeyPair(
            Bytes32.fromHexString(
                "0xc87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3"));

    nodeA =
        besu.createNode(
            "nodeA",
            nodeBuilder ->
                configureNode(
                    nodeBuilder,
                    nodeAP2pBindingPort,
                    nodeAKeyPair,
                    nodeBKeyPair.getPublicKey(),
                    nodeBP2pBindingPort));
    nodeB =
        besu.createNode(
            "nodeB",
            nodeBuilder ->
                configureNode(
                    nodeBuilder,
                    nodeBP2pBindingPort,
                    nodeBKeyPair,
                    nodeAKeyPair.getPublicKey(),
                    nodeAP2pBindingPort));
    cluster.start(nodeA, nodeB);
  }

  private KeyPair createKeyPair(final Bytes32 privateKey) {
    return KeyPair.create(SECPPrivateKey.create(privateKey, ALGORITHM), curve, ALGORITHM);
  }

  @Test
  public void shouldConnectBothNodesConfiguredInGenesisFile() {
    nodeA.verify(net.awaitPeerCount(1));
    nodeB.verify(net.awaitPeerCount(1));
  }

  private BesuNodeConfigurationBuilder configureNode(
      final BesuNodeConfigurationBuilder nodeBuilder,
      final int p2pBindingPort,
      final KeyPair keyPair,
      final SECPPublicKey peerPublicKey,
      final int peerP2pBindingPort) {
    return nodeBuilder
        .devMode(false)
        .keyPair(keyPair)
        .p2pPort(p2pBindingPort)
        .genesisConfigProvider(
            (nodes) ->
                Optional.of(
                    String.format(
                        "{\"config\": {\"ethash\": {}, \"discovery\": { \"bootnodes\": [\"enode://%s@127.0.0.1:%d\"]}}, \"gasLimit\": \"0x1\", \"difficulty\": \"0x1\"}",
                        peerPublicKey.toString().substring(2), peerP2pBindingPort)))
        .bootnodeEligible(false)
        .jsonRpcEnabled()
        .jsonRpcAdmin();
  }
}
