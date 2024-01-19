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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.bft.BftAcceptanceTestParameterization;
import org.hyperledger.besu.tests.acceptance.bft.ParameterizedBftTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BftMiningSoakTest extends ParameterizedBftTestBase {

  private final int NUM_STEPS = 5;

  static int getTestDurationMins() {
    // Use a soak time of 30 mins
    return Integer.getInteger("acctests.soakTimeMins", 30);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldBeStableDuringLongTest(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);

    // There is a minimum amount of time the test can be run for, due to hard coded delays
    // in between certain steps. There should be no upper-limit to how long the test is run for
    assertThat(getTestDurationMins()).isGreaterThanOrEqualTo(20);

    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");

    assertThat(getTestDurationMins() / NUM_STEPS).isGreaterThanOrEqualTo(3);

    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");
    receiver.balanceEquals(20);

    /*********************************************************************/
    /* Setup                                                             */
    /*********************************************************************/
    for (int i = 0; i < 5; i++) {
      minerNode1.execute(accountTransactions.createTransfer(sender, 50));
    }
    cluster.verify(sender.balanceEquals(150));

    minerNode2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode3.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));

    minerNode4.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 3));
    cluster.verify(receiver.balanceEquals(6));

    /*********************************************************************/
    /* Step 1                                                            */
    /* Run for the configured time period, periodically checking that    */
    /* the chain is progressing as expected                              */
    /*********************************************************************/
    BigInteger chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    assertThat(chainHeight.compareTo(BigInteger.ZERO)).isGreaterThanOrEqualTo(1);
    BigInteger lastChainHeight = chainHeight;

    Instant startTime = Instant.now();
    Instant nextStepEndTime = startTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // With 1-second block period chain height should have moved on by at least 50 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
            .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }
    Instant previousStepEndTime = Instant.now();

    /*********************************************************************/
    /* Step 2                                                            */
    /* Stop one of the nodes, check that the chain continues mining      */
    /* blocks                                                            */
    /*********************************************************************/
    cluster.stopNode(minerNode4);

    nextStepEndTime = previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should have moved on by at least 5 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(20))))
                .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }
    previousStepEndTime = Instant.now();

    /*********************************************************************/
    /* Step 3                                                            */
    /* Stop another one of the nodes, check that the chain now stops     */
    /* mining blocks                                                     */
    /*********************************************************************/
    cluster.stopNode(minerNode3);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    lastChainHeight = chainHeight;

    nextStepEndTime = previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should not have moved on
        assertThat(chainHeight.equals(lastChainHeight)).isTrue();
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }

    // Give it time to start
    try {
      Thread.sleep(900000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    // System.exit(0);

    /*********************************************************************/
    /* Step 4                                                            */
    /* Restart both of the stopped nodes. Check that the chain resumes   */
    /* mining blocks                                                     */
    /*********************************************************************/
    cluster.startNode(minerNode4);

    // Give it time to start
    try {
      Thread.sleep(30000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.startNode(minerNode3);

    // Give them time to sync
    try {
      Thread.sleep(120000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }
    System.exit(0);
    previousStepEndTime = Instant.now();

    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime = previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    // This loop gives it time to sync
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }
    previousStepEndTime = Instant.now();

    // By this loop it should be producing blocks again
    nextStepEndTime = previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should have moved on by at least 5 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
                 .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }

    /*********************************************************************/
    /* Upgrade the chain from berlin to london                           */
    /*********************************************************************/
    cluster.stopNode(minerNode1);

    // Give it time to stop
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    updateGenesisConfigToLondon(minerNode1, true, 200);

    cluster.startNode(minerNode1);

    // Give it time to start
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode2);

    // Give it time to stop
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    updateGenesisConfigToLondon(minerNode2, true, 200);

    cluster.startNode(minerNode2);

    // Give it time to start
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode3);

    // Give it time to stop
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    updateGenesisConfigToLondon(minerNode3, true, 200);

    cluster.startNode(minerNode3);

    // Give it time to start
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode4);

    // Give it time to stop
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    updateGenesisConfigToLondon(minerNode4, true, 200);

    cluster.startNode(minerNode4);

    // Give it time to start
    try {
      Thread.sleep(60000);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }
    previousStepEndTime = Instant.now();

    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime = previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(60000);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should have moved on by at least 5 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
                .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }
  }

  private static void updateGenesisConfigToLondon(
          final BesuNode minerNode, final boolean zeroBaseFeeEnabled, final int blockNumber) {

    if (minerNode.getGenesisConfig().isPresent()) {
      final ObjectNode genesisConfigNode3 = JsonUtil.objectNodeFromString(minerNode.getGenesisConfig().get());
      final ObjectNode config1 = (ObjectNode) genesisConfigNode3.get("config");
      config1.put("londonBlock", blockNumber);
      config1.put("zeroBaseFee", zeroBaseFeeEnabled);
      minerNode.setGenesisConfig(genesisConfigNode3.toString());
    }
  }
}
