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
package org.hyperledger.besu.tests.acceptance.dsl.condition.blockchain;

import static org.junit.jupiter.api.Assertions.fail;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;

public class ExpectBlockNotCreated implements Condition {
  private final EthTransactions eth;
  private final int initialWait;
  private final int blockPeriodWait;

  public ExpectBlockNotCreated(
      final EthTransactions eth, final int initialWait, final int blockPeriodWait) {
    this.eth = eth;
    this.initialWait = initialWait;
    this.blockPeriodWait = blockPeriodWait;
  }

  @Override
  public void verify(final Node node) {
    final AtomicInteger tries = new AtomicInteger();
    final int triesRequired = blockPeriodWait / 1000;
    final BigInteger initialBlock = initialBlock(node);
    Awaitility.await()
        .pollInterval(1, TimeUnit.SECONDS)
        .atMost(30, TimeUnit.SECONDS)
        .until(() -> isNewBlock(node, tries, triesRequired, initialBlock));
  }

  private Boolean isNewBlock(
      final Node node,
      final AtomicInteger tries,
      final int triesRequired,
      final BigInteger lastBlock) {
    final BigInteger currentBlock = node.execute(eth.blockNumber());
    final int currentTries = tries.getAndIncrement();
    if (!currentBlock.equals(lastBlock)) {
      final String failMsg =
          String.format(
              "New block created. Expected block=%s got block=%s", lastBlock, currentBlock);
      fail(failMsg);
    }
    // Only consider that the block is really the same after a period of time or number tries as a
    // block could be currently be in the process of being mined
    return currentTries > triesRequired;
  }

  private BigInteger initialBlock(final Node node) {
    try {
      Thread.sleep(initialWait);
      return node.execute(eth.blockNumber());
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
