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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.math.BigInteger;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EthSendRawTransactionAcceptanceTest extends AcceptanceTestBase {
  private static final long CHAIN_ID = 20211;

  private Account sender;

  private Node lenientNode;
  private Node strictNode;
  private Node miningNode;

  @BeforeEach
  public void setUp() throws Exception {
    sender = accounts.getPrimaryBenefactor();

    lenientNode = besu.createArchiveNode("lenientNode", configureNode((false)));
    strictNode = besu.createArchiveNode("strictNode", configureNode((true)));
    miningNode = besu.createMinerNode("strictMiningNode", configureNode((true)));
    cluster.start(lenientNode, strictNode, miningNode);

    // verify nodes are fully connected otherwise tx could not be propagated
    lenientNode.verify(net.awaitPeerCount(2));
    strictNode.verify(net.awaitPeerCount(2));
    miningNode.verify(net.awaitPeerCount(2));

    // verify that the miner started producing blocks and all other nodes are syncing from it
    waitForBlockHeight(miningNode, 1);
    final var minerChainHead = miningNode.execute(ethTransactions.block());
    lenientNode.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));
    strictNode.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));
  }

  @Test
  public void shouldSendSuccessfullyToLenientNodeWithoutChainId() {
    final TransferTransaction tx = createTransactionWithoutChainId();
    final String rawTx = tx.signedTransactionData();
    final String txHash = tx.transactionHash();

    lenientNode.verify(eth.expectSuccessfulEthRawTransaction(rawTx));

    // Tx should be included on-chain
    miningNode.verify(eth.expectSuccessfulTransactionReceipt(txHash));
  }

  @Test
  public void shouldFailToSendToToStrictNodeWithoutChainId() {
    final TransferTransaction tx = createTransactionWithoutChainId();
    final String rawTx = tx.signedTransactionData();

    strictNode.verify(eth.expectEthSendRawTransactionException(rawTx, "ChainId is required"));
  }

  @Test
  public void shouldFailToSendWithInvalidRlp() {
    final String invalidRawTx = "0x5555";
    strictNode.verify(eth.expectEthSendRawTransactionException(invalidRawTx, "Invalid params"));
  }

  @Test
  public void shouldSendSuccessfullyWithChainId_lenientNode() {
    final TransferTransaction tx = createTransactionWithChainId();
    final String rawTx = tx.signedTransactionData();
    final String txHash = tx.transactionHash();

    lenientNode.verify(eth.expectSuccessfulEthRawTransaction(rawTx));
    // Tx should be included on-chain
    miningNode.verify(eth.expectSuccessfulTransactionReceipt(txHash));
  }

  @Test
  public void shouldSendSuccessfullyWithChainId_strictNode() {
    final TransferTransaction tx = createTransactionWithChainId();
    final String rawTx = tx.signedTransactionData();
    final String txHash = tx.transactionHash();

    strictNode.verify(eth.expectSuccessfulEthRawTransaction(rawTx));
    // Tx should be included on-chain
    miningNode.verify(eth.expectSuccessfulTransactionReceipt(txHash));
  }

  private TransferTransaction createTransactionWithChainId() {
    return createTransaction(true);
  }

  private TransferTransaction createTransactionWithoutChainId() {
    return createTransaction(false);
  }

  private TransferTransaction createTransaction(final boolean withChainId) {
    if (withChainId) {
      return accountTransactions.create1559Transfer(createAccount(), 2, CHAIN_ID);
    } else {
      final BigInteger nonce =
          miningNode.execute(ethTransactions.getTransactionCount(sender.getAddress()));
      return accountTransactions.createTransfer(
          accounts.getPrimaryBenefactor(), createAccount(), 1, nonce);
    }
  }

  private UnaryOperator<BesuNodeConfigurationBuilder> configureNode(
      final boolean enableStrictReplayProtection) {
    return b ->
        b.genesisConfigProvider(GenesisConfigurationFactory::createDevLondonGenesisConfig)
            .strictTxReplayProtectionEnabled(enableStrictReplayProtection)
            .devMode(false);
  }

  private Account createAccount() {
    return accounts.createAccount("Test account");
  }
}
