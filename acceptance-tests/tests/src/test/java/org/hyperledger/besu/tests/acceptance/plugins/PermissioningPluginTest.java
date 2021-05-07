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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class PermissioningPluginTest extends AcceptanceTestBase {
  private BesuNode aliceNode;
  private BesuNode bobNode;
  private BesuNode charlieNode;

  @Before
  public void setUp() throws Exception {
    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .plugins(Collections.singletonList("testPlugins"))
            .jsonRpcEnabled()
            .jsonRpcTxPool()
            .jsonRpcAdmin();

    aliceNode = besu.create(builder.name("alice").miningEnabled(false).keyFilePath("key").build());

    bobNode = besu.create(builder.name("bob").miningEnabled(false).keyFilePath("key1").build());

    charlieNode =
        besu.create(builder.name("charlie").miningEnabled(false).keyFilePath("key2").build());

    cluster.startNode(aliceNode);
    aliceNode.verify(net.awaitPeerCount(1));
    cluster.startNode(bobNode);
    bobNode.verify(net.awaitPeerCount(1));
    cluster.startNode(charlieNode);
    charlieNode.verify(net.awaitPeerCount(2));
  }

  @Test
  public void blockedConnectionNodeCanOnlyConnectToTransactionNode() {
    aliceNode.verify(admin.doesNotHavePeer(bobNode));
    aliceNode.verify(admin.hasPeer(charlieNode));

    bobNode.verify(admin.doesNotHavePeer(aliceNode));
    bobNode.verify(admin.hasPeer(charlieNode));

    charlieNode.verify(admin.hasPeer(aliceNode));
    charlieNode.verify(admin.hasPeer(bobNode));
  }

  @Test
  public void transactionsAreNotSendToBlockPendingTransactionsNode() {
    final Account account = accounts.createAccount("account-one");
    final Amount balance = Amount.ether(20);

    TransferTransaction tx = accountTransactions.createTransfer(account, balance);

    Hash txHash = aliceNode.execute(tx);

    aliceNode.verify(txPoolConditions.inTransactionPool(txHash));
    bobNode.verify(txPoolConditions.inTransactionPool(txHash));
    charlieNode.verify(txPoolConditions.notInTransactionPool(txHash));
  }
}
