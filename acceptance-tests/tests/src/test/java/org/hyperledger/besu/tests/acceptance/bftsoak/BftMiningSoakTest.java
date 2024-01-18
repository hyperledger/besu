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
package org.hyperledger.besu.tests.acceptance.bftsoak;

import org.hyperledger.besu.tests.acceptance.bft.BftAcceptanceTestParameterization;
import org.hyperledger.besu.tests.acceptance.bft.ParameterizedBftTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BftMiningSoakTest extends ParameterizedBftTestBase {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldMineOnMultipleNodes() throws Exception {
    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");
    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");
    receiver.balanceEquals(20);

    BigInteger currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the beginning: " + currentReceiverBalance);
    BigInteger chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    for (int i = 0; i < 5; i++) {
      BigInteger currentSenderBalance =
          minerNode1.execute(ethTransactions.getBalanceAtBlock(sender, BigInteger.valueOf(0)));
      System.out.println("MRW: Rich donor  balance: " + currentSenderBalance);
      minerNode1.execute(accountTransactions.createTransfer(sender, 50));
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());
      System.out.println("MRW: Current chain height: " + chainHeight);
    }
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    cluster.verify(sender.balanceEquals(150));

    minerNode2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode3.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));

    minerNode4.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 3));
    cluster.verify(receiver.balanceEquals(6));

    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
    currentReceiverBalance =
        minerNode1.execute(ethTransactions.getBalanceAtBlock(receiver, BigInteger.valueOf(0)));
    System.out.println("MRW: Rich donor balance at the end: " + currentReceiverBalance);
    Thread.sleep(5000);
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    System.out.println("MRW: Current chain height: " + chainHeight);
  }

  /* private static void updateGenesisConfigToLondon(
      final BesuNode minerNode, final boolean zeroBaseFeeEnabled) {
    final Optional<String> genesisConfig =
        minerNode.getGenesisConfigProvider().create(List.of(minerNode));
    final ObjectNode genesisConfigNode = JsonUtil.objectNodeFromString(genesisConfig.orElseThrow());
    final ObjectNode config = (ObjectNode) genesisConfigNode.get("config");
    config.remove("berlinBlock");
    config.put("londonBlock", 0);
    config.put("zeroBaseFee", zeroBaseFeeEnabled);
    minerNode.setGenesisConfig(genesisConfigNode.toString());
  }*/
}
