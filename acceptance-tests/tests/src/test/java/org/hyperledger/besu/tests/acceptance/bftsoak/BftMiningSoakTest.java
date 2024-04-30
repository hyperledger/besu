/*
 * Copyright Hyperledger Besu contributors.
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

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.bft.BftAcceptanceTestParameterization;
import org.hyperledger.besu.tests.acceptance.bft.ParameterizedBftTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.web3j.generated.SimpleStorage;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BftMiningSoakTest extends ParameterizedBftTestBase {

  private final int NUM_STEPS = 5;

  private final int MIN_TEST_TIME_MINS = 60;

  private static final long ONE_MINUTE = Duration.of(1, ChronoUnit.MINUTES).toMillis();

  private static final long TEN_SECONDS = Duration.of(10, ChronoUnit.SECONDS).toMillis();

  static int getTestDurationMins() {
    // Use a default soak time of 60 mins
    return Integer.getInteger("acctests.soakTimeMins", 60);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldBeStableDuringLongTest(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);

    // There is a minimum amount of time the test can be run for, due to hard coded delays
    // in between certain steps. There should be no upper-limit to how long the test is run for
    assertThat(getTestDurationMins()).isGreaterThanOrEqualTo(MIN_TEST_TIME_MINS);

    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");

    // Each step should be given a minimum of 3 minutes to complete successfully. If the time
    // give to run the soak test results in a time-per-step lower than this then the time
    // needs to be increased.
    assertThat(getTestDurationMins() / NUM_STEPS).isGreaterThanOrEqualTo(3);

    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

    /*********************************************************************/
    /* Setup                                                             */
    /* Deploy a contract that we'll invoke periodically to ensure state  */
    /* is correct during the test, especially after stopping nodes and   */
    /* applying new forks.                                               */
    /*********************************************************************/
    final SimpleStorage simpleStorageContract =
        minerNode1.execute(contractTransactions.createSmartContract(SimpleStorage.class));

    // Check the contract address is as expected for this sender & nonce
    contractVerifier
        .validTransactionReceipt("0x42699a7612a82f1d9c36148af9c77354759b210b")
        .verify(simpleStorageContract);

    // Should initially be set to 0
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(0));

    // Set to something new
    simpleStorageContract.set(BigInteger.valueOf(101)).send();

    // Check the state of the contract has updated correctly. We'll set & get this several times
    // during the test
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(101));

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
        Thread.sleep(ONE_MINUTE);
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

    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(ONE_MINUTE);
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
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    lastChainHeight = chainHeight;

    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(ONE_MINUTE);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should not have moved on
        assertThat(chainHeight.equals(lastChainHeight)).isTrue();
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }

    /*********************************************************************/
    /* Step 4                                                            */
    /* Restart both of the stopped nodes. Check that the chain resumes   */
    /* mining blocks                                                     */
    /*********************************************************************/
    cluster.startNode(minerNode4);

    // Give it time to start
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.startNode(minerNode3);

    // Give it time to start
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    previousStepEndTime = Instant.now();

    // This step gives the stalled chain time to re-sync and agree on the next BFT round.
    // The time this takes is proportional to the time the chain was stalled for, because
    // the time between BFT rounds increases over time.
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime =
        previousStepEndTime.plus((getTestDurationMins() / NUM_STEPS + 2), ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(ONE_MINUTE);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }
    previousStepEndTime = Instant.now();

    // By this loop it should be producing blocks again
    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(ONE_MINUTE);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should have moved on by at least 50 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
            .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }

    /*********************************************************************/
    /* Update our smart contract before upgrading from berlin to london  */
    /*********************************************************************/
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(101));
    simpleStorageContract.set(BigInteger.valueOf(201)).send();
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(201));

    /*********************************************************************/
    /* Upgrade the chain from berlin to london                           */
    /*********************************************************************/
    cluster.stopNode(minerNode1);

    // Give it time to stop
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    final int londonBlockNumber = lastChainHeight.intValue() + 120;

    // Upgrade to London in 120 blocks time
    updateGenesisConfigToLondon(minerNode1, true, londonBlockNumber);

    // Restart it
    cluster.startNode(minerNode1);

    // Give it time to start
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode2);

    // Give it time to stop
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    // Upgrade to London in 120 blocks time
    updateGenesisConfigToLondon(minerNode2, true, londonBlockNumber);

    // Restart it
    cluster.startNode(minerNode2);

    // Give it time to start
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode3);

    // Give it time to stop
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    // Upgrade to London in 120 blocks time
    updateGenesisConfigToLondon(minerNode3, true, londonBlockNumber);

    // Restart it
    cluster.startNode(minerNode3);

    // Give it time to start
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    cluster.stopNode(minerNode4);

    // Give it time to stop
    try {
      Thread.sleep(TEN_SECONDS);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }

    // Upgrade to London in 120 block time
    updateGenesisConfigToLondon(minerNode4, true, londonBlockNumber);

    // Restart it
    cluster.startNode(minerNode4);

    // Give it time to start
    try {
      Thread.sleep(ONE_MINUTE);
    } catch (InterruptedException e) {
      Assertions.fail(e);
    }
    previousStepEndTime = Instant.now();

    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      try {
        Thread.sleep(ONE_MINUTE);
        chainHeight = minerNode1.execute(ethTransactions.blockNumber());

        // Chain height should have moved on by at least 50 blocks
        assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
            .isGreaterThanOrEqualTo(1);
        lastChainHeight = chainHeight;
      } catch (InterruptedException e) {
        Assertions.fail(e);
        break;
      }
    }

    /*********************************************************************/
    /* Check that the state of our smart contract is still correct       */
    /*********************************************************************/
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(201));

    /*********************************************************************/
    /* Update it once more to check new transactions are mined OK        */
    /*********************************************************************/
    simpleStorageContract.set(BigInteger.valueOf(301)).send();
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(301));
  }

  private static void updateGenesisConfigToLondon(
      final BesuNode minerNode, final boolean zeroBaseFeeEnabled, final int blockNumber) {

    if (minerNode.getGenesisConfig().isPresent()) {
      final ObjectNode genesisConfigNode3 =
          JsonUtil.objectNodeFromString(minerNode.getGenesisConfig().get());
      final ObjectNode config1 = (ObjectNode) genesisConfigNode3.get("config");
      config1.put("londonBlock", blockNumber);
      config1.put("zeroBaseFee", zeroBaseFeeEnabled);
      minerNode.setGenesisConfig(genesisConfigNode3.toString());
    }
  }
}
