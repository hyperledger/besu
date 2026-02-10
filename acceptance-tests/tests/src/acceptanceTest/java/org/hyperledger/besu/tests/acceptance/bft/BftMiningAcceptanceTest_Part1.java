/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration.MutableInitValues;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BftMiningAcceptanceTest_Part1 extends ParameterizedBftTestBase {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldMineOnSingleNodeWithPaidGas_Berlin(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);
    final BesuNode minerNode = nodeFactory.createNode(besu, "miner1");
    cluster.start(minerNode);

    cluster.verify(blockchain.reachesHeight(minerNode, 1));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldMineOnSingleNodeWithFreeGas_Berlin(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);
    final BesuNode minerNode = nodeFactory.createNode(besu, "miner1");
    final MiningConfiguration zeroGasMiningParams =
        ImmutableMiningConfiguration.builder()
            .mutableInitValues(
                MutableInitValues.builder()
                    .isMiningEnabled(true)
                    .minTransactionGasPrice(Wei.ZERO)
                    .coinbase(AddressHelpers.ofValue(1))
                    .build())
            .build();
    minerNode.setMiningParameters(zeroGasMiningParams);

    cluster.start(minerNode);

    cluster.verify(blockchain.reachesHeight(minerNode, 1));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode.execute(accountTransactions.createTransfer(sender, 50, Amount.ZERO));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(
        accountTransactions.createIncrementalTransfers(sender, receiver, 1, Amount.ZERO));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(
        accountTransactions.createIncrementalTransfers(sender, receiver, 2, Amount.ZERO));
    cluster.verify(receiver.balanceEquals(3));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldMineOnSingleNodeWithPaidGas_London(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);
    final BesuNode minerNode = nodeFactory.createNode(besu, "miner1");
    updateGenesisConfigToLondon(minerNode, false);

    cluster.start(minerNode);

    cluster.verify(blockchain.reachesHeight(minerNode, 1));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(accountTransactions.create1559Transfer(sender, 50, 4));
    cluster.verify(sender.balanceEquals(100));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(accountTransactions.create1559IncrementalTransfers(sender, receiver, 2, 4));
    cluster.verify(receiver.balanceEquals(3));
  }
}
