/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.qbft.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class TransitionsTest {

  public static final Address NODE_ADDRESS =
      Address.fromHexString("0xeac51e3fe1afc9894f0dfeab8ceb471899b932df");
  public static final Bytes32 NODE_PRIVATE_KEY =
      Bytes32.fromHexString("0xa3bdf521b0f286a80918c4b67000dfd2a2bdef97e94d268016ef9ec86648eac3");

  @Test
  public void transitionsBlockPeriod() throws InterruptedException {
    final TestClock clock = new TestClock(Instant.EPOCH);

    final List<QbftFork> qbftForks =
        List.of(
            new QbftFork(
                JsonUtil.objectNodeFromMap(
                    Map.of(
                        BftFork.FORK_BLOCK_KEY, (long) 1, BftFork.BLOCK_PERIOD_SECONDS_KEY, 10))),
            new QbftFork(
                JsonUtil.objectNodeFromMap(
                    Map.of(
                        BftFork.FORK_BLOCK_KEY, (long) 2, BftFork.BLOCK_PERIOD_SECONDS_KEY, 20))));

    final BftEventQueue bftEventQueue = new BftEventQueue(TestContextBuilder.MESSAGE_QUEUE_LIMIT);
    final TestContext context =
        new TestContextBuilder()
            .indexOfFirstLocallyProposedBlock(0)
            .nodeParams(
                List.of(new NodeParams(NODE_ADDRESS, NodeKeyUtils.createFrom(NODE_PRIVATE_KEY))))
            .clock(clock)
            .useValidatorContract(false)
            .qbftForks(qbftForks)
            .eventQueue(bftEventQueue)
            .buildAndStart();

    clock.stepMillis(10_000);
    context.getEventMultiplexer().handleBftEvent(bftEventQueue.poll(1, TimeUnit.SECONDS));

    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(context.getBlockchain().getChainHeadHeader()));
    clock.stepMillis(20_000);
    context.getEventMultiplexer().handleBftEvent(bftEventQueue.poll(1, TimeUnit.SECONDS));

    final BlockHeader genesisBlock = context.getBlockchain().getBlockHeader(0).get();
    final BlockHeader blockHeader1 = context.getBlockchain().getBlockHeader(1).get();
    final BlockHeader blockHeader2 = context.getBlockchain().getBlockHeader(2).get();

    assertThat(blockHeader1.getTimestamp()).isEqualTo(genesisBlock.getTimestamp() + 10);
    assertThat(blockHeader2.getTimestamp()).isEqualTo(blockHeader1.getTimestamp() + 20);
  }
}
