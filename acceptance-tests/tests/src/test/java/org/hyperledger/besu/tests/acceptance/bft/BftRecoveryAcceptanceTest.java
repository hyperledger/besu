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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class BftRecoveryAcceptanceTest extends AcceptanceTestBase {

  private static final long THREE_MINUTES = Duration.of(3, ChronoUnit.MINUTES).toMillis();
  private static final long TEN_SECONDS = Duration.of(10, ChronoUnit.SECONDS).toMillis();

  @Test
  public void shouldBeRecoverFast() throws Exception {

    List<String> extraCLIOptions =
        List.of("--Xqbft-fast-recovery", "--Xqbft-enable-early-round-change");

    // Create a mix of Bonsai and Forest DB nodes
    final BesuNode minerNode1 =
        besu.createQbftNodeWithExtraCliOptions(
            "miner1", true, DataStorageFormat.BONSAI, extraCLIOptions);
    final BesuNode minerNode2 =
        besu.createQbftNodeWithExtraCliOptions(
            "miner2", true, DataStorageFormat.BONSAI, extraCLIOptions);
    final BesuNode minerNode3 =
        besu.createQbftNodeWithExtraCliOptions(
            "miner3", true, DataStorageFormat.BONSAI, extraCLIOptions);
    final BesuNode minerNode4 =
        besu.createQbftNodeWithExtraCliOptions(
            "miner4", true, DataStorageFormat.BONSAI, extraCLIOptions);

    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.verify(blockchain.reachesHeight(minerNode1, 20, 85));

    stopNode(minerNode3);
    stopNode(minerNode4);

    /* Let the other two nodes go to round 5 */
    Thread.sleep(THREE_MINUTES);

    startNode(minerNode3);
    startNode(minerNode4);

    /* Mining should start in few seconds */
    cluster.verify(blockchain.reachesHeight(minerNode3, 1, 20));
  }

  // Start a node with a delay before returning to give it time to start
  private void startNode(final BesuNode node) throws InterruptedException {
    cluster.startNode(node);
    Thread.sleep(TEN_SECONDS);
  }

  // Stop a node with a delay before returning to give it time to stop
  private void stopNode(final BesuNode node) throws InterruptedException {
    cluster.stopNode(node);
    Thread.sleep(TEN_SECONDS);
  }

  @AfterEach
  @Override
  public void tearDownAcceptanceTestBase() {
    cluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
