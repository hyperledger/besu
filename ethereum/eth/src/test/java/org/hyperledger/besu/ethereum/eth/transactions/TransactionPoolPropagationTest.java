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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionPoolPropagationTest {

  final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setEnabled(false);

  private Vertx vertx;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /** Helper to do common setup tasks. */
  private void initTest(final TestNodeList txNodes) throws Exception {
    txNodes.connectAndAssertAll();
    txNodes.logPeerConnections();
    txNodes.assertPeerCounts();
    txNodes.assertPeerConnections();
  }

  /** Helper to do common wrapup tasks. */
  private void wrapup(final TestNodeList txNodes) {
    txNodes.assertNoNetworkDisconnections();
    txNodes.assertPeerCounts();
    txNodes.assertPeerConnections();
  }

  /**
   * 2nd order test to verify the framework correctly fails if a disconnect occurs It could have a
   * more detailed exception check - more than just the class.
   */
  @Test
  void disconnectShouldThrow() throws Exception {

    try (final TestNodeList txNodes = new TestNodeList()) {
      // Create & Start Nodes
      final TestNode node1 = txNodes.create(vertx, null, null, noDiscovery);
      txNodes.create(vertx, null, null, noDiscovery);
      txNodes.create(vertx, null, null, noDiscovery);

      initTest(txNodes);

      node1.network.getPeers().iterator().next().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);

      assertThatThrownBy(() -> wrapup(txNodes)).isInstanceOf(AssertionError.class);
    }
  }

  /**
   * Simulate a 4-node cluster. Send at least 1 Tx to each node, and multiple Tx to at least one
   * node. Verify that all nodes get the correct number of pending transactions.
   */
  @Test
  void shouldPropagateLocalAndRemoteTransactions() throws Exception {
    try (final TestNodeList nodes = new TestNodeList()) {
      // Create & Start Nodes
      final TestNode node1 = nodes.create(vertx, null, null, noDiscovery);
      final TestNode node2 = nodes.create(vertx, null, null, noDiscovery);
      final TestNode node3 = nodes.create(vertx, null, null, noDiscovery);
      final TestNode node4 = nodes.create(vertx, null, null, noDiscovery);
      final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
      final KeyPair keyPair =
          signatureAlgorithm.createKeyPair(
              signatureAlgorithm.createPrivateKey(
                  Bytes32.fromHexString(
                      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")));
      final TransactionTestFixture transactionBuilder = new TransactionTestFixture();
      transactionBuilder.gasLimit(1_000_000);
      final Transaction transaction1 = transactionBuilder.nonce(0).createTransaction(keyPair);
      final Transaction transaction2 = transactionBuilder.nonce(1).createTransaction(keyPair);
      final Transaction transaction3 = transactionBuilder.nonce(2).createTransaction(keyPair);
      final Transaction transaction4 = transactionBuilder.nonce(3).createTransaction(keyPair);
      final Transaction transaction5 = transactionBuilder.nonce(4).createTransaction(keyPair);
      initTest(nodes);
      node1.receiveRemoteTransaction(transaction1);
      waitForPendingTransactionCounts(nodes, 1);

      node2.receiveRemoteTransaction(transaction2);
      waitForPendingTransactionCounts(nodes, 2);

      node3.receiveRemoteTransaction(transaction3);
      waitForPendingTransactionCounts(nodes, 3);

      node4.receiveRemoteTransaction(transaction4);
      waitForPendingTransactionCounts(nodes, 4);

      node3.receiveLocalTransaction(transaction5);
      waitForPendingTransactionCounts(nodes, 5);
    }
  }

  private void waitForPendingTransactionCounts(final TestNodeList nodes, final int expected) {
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> nodes.assertPendingTransactionCounts(expected, expected, expected, expected));
  }
}
