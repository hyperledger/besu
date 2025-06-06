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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.SignUtil;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

public class PermissioningPluginTest extends AcceptanceTestBase {
  private BesuNode minerNode;

  private BesuNode aliceNode;
  private BesuNode bobNode;
  private BesuNode charlieNode;

  private static final long GAS_LIMIT_THRESHOLD = 22000L;

  @BeforeEach
  public void setUp() throws Exception {
    minerNode = besu.create(createNodeBuilder().name("miner").build());

    aliceNode = besu.create(createNodeBuilder().name("alice").keyFilePath("key").build());

    bobNode = besu.create(createNodeBuilder().name("bob").keyFilePath("key1").build());

    charlieNode = besu.create(createNodeBuilder().name("charlie").keyFilePath("key2").build());

    cluster.start(minerNode, charlieNode);

    cluster.startNode(aliceNode);
    aliceNode.awaitPeerDiscovery(net.awaitPeerCount(2));

    cluster.startNode(bobNode);
    bobNode.awaitPeerDiscovery(net.awaitPeerCount(2));
  }

  private BesuNodeConfigurationBuilder createNodeBuilder() {
    return new BesuNodeConfigurationBuilder()
        .miningEnabled(false)
        .plugins(List.of("testPlugins"))
        .extraCLIOptions(List.of("--plugin-permissioning-test-enabled=true"))
        .jsonRpcEnabled()
        .jsonRpcTxPool()
        .jsonRpcAdmin();
  }

  @Disabled("flaky see https://github.com/hyperledger/besu/issues/8726")
  @Test
  public void blockedConnectionNodeCanOnlyConnectToTransactionNode() {
    minerNode.verify(admin.hasPeer(aliceNode));
    minerNode.verify(admin.hasPeer(bobNode));
    minerNode.verify(admin.hasPeer(charlieNode));

    aliceNode.verify(admin.doesNotHavePeer(bobNode));
    aliceNode.verify(admin.hasPeer(minerNode));
    aliceNode.verify(admin.hasPeer(charlieNode));

    bobNode.verify(admin.hasPeer(minerNode));
    bobNode.verify(admin.doesNotHavePeer(aliceNode));
    bobNode.verify(admin.hasPeer(charlieNode));

    charlieNode.verify(admin.hasPeer(minerNode));
    charlieNode.verify(admin.hasPeer(aliceNode));
    charlieNode.verify(admin.hasPeer(bobNode));
  }

  @Test
  public void transactionsAreNotSendToBlockPendingTransactionsNode() {
    final Account account = accounts.createAccount("account-one");
    final Amount balance = Amount.ether(20);

    final TransferTransaction tx = accountTransactions.createTransfer(account, balance);

    final Hash txHash = aliceNode.execute(tx);

    aliceNode.verify(txPoolConditions.inTransactionPool(txHash));
    bobNode.verify(txPoolConditions.inTransactionPool(txHash));
    charlieNode.verify(txPoolConditions.notInTransactionPool(txHash));
    minerNode.verify(txPoolConditions.inTransactionPool(txHash));
  }

  @Test
  public void allowFilteredByGasLimit() {

    final Account sender = accounts.getPrimaryBenefactor();
    final Account recipient = accounts.createAccount("account-two");
    final BigInteger GAS_LIMIT = BigInteger.valueOf(GAS_LIMIT_THRESHOLD + 100);
    final BigInteger GAS_PRICE = BigInteger.valueOf(1000);
    final Amount amount = Amount.wei(BigInteger.valueOf(29));

    final RawTransaction tx =
        RawTransaction.createEtherTransaction(
            sender.getNextNonce(),
            GAS_PRICE,
            GAS_LIMIT,
            recipient.getAddress(),
            Convert.toWei(amount.getValue(), amount.getUnit()).toBigIntegerExact());

    final String rawSigned =
        Numeric.toHexString(
            SignUtil.signTransaction(tx, sender, new SECP256K1(), Optional.empty()));
    final String txHash = aliceNode.execute(ethTransactions.sendRawTransaction(rawSigned));

    aliceNode.verify(txPoolConditions.inTransactionPool(Hash.fromHexString(txHash)));
  }

  @Test
  public void blockedFilteredByGasLimit() {
    final Account sender = accounts.getPrimaryBenefactor();
    final Account recipient = accounts.createAccount("account-two");
    final BigInteger GAS_LIMIT = BigInteger.valueOf(GAS_LIMIT_THRESHOLD - 100);
    final BigInteger GAS_PRICE = BigInteger.valueOf(1000);
    final Amount amount = Amount.wei(BigInteger.valueOf(29));

    final RawTransaction tx =
        RawTransaction.createEtherTransaction(
            sender.getNextNonce(),
            GAS_PRICE,
            GAS_LIMIT,
            recipient.getAddress(),
            Convert.toWei(amount.getValue(), amount.getUnit()).toBigIntegerExact());

    final String rawSigned =
        Numeric.toHexString(
            SignUtil.signTransaction(tx, sender, new SECP256K1(), Optional.empty()));

    assertThatThrownBy(() -> aliceNode.execute(ethTransactions.sendRawTransaction(rawSigned)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("not authorized");
  }
}
