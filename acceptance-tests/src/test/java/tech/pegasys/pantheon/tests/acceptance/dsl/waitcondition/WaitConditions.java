/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.waitcondition;

import static tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions.LATEST;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.blockchain.ExpectBlockNumberAbove;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft.IbftTransactions;

import java.math.BigInteger;

public class WaitConditions {
  private final EthTransactions eth;
  private final CliqueTransactions clique;
  private final IbftTransactions ibft;

  public WaitConditions(
      final EthTransactions eth, final CliqueTransactions clique, final IbftTransactions ibft) {
    this.eth = eth;
    this.clique = clique;
    this.ibft = ibft;
  }

  public WaitCondition chainHeadHasProgressedByAtLeast(
      final PantheonNode node, final int blocksAheadOfLatest) {
    final BigInteger futureBlock =
        node.execute(eth.blockNumber()).add(BigInteger.valueOf(blocksAheadOfLatest));
    return new ExpectBlockNumberAbove(eth, futureBlock)::verify;
  }

  public WaitCondition chainHeadHasProgressedByAtLeast(
      final PantheonNode node, final int blocksAheadOfLatest, final int timeout) {
    final BigInteger futureBlock =
        node.execute(eth.blockNumber()).add(BigInteger.valueOf(blocksAheadOfLatest));
    return new ExpectBlockNumberAbove(eth, futureBlock, timeout)::verify;
  }

  public WaitCondition cliqueValidatorsChanged(final Node node) {
    return new WaitUntilSignersChanged(node.execute(clique.createGetSigners(LATEST)), clique);
  }

  public WaitCondition ibftValidatorsChanged(final Node node) {
    return new WaitUntilValidatorsChanged(node.execute(ibft.createGetValidators(LATEST)), ibft);
  }

  public WaitCondition chainHeadIsAtLeast(final long blockNumber) {
    return new ExpectBlockNumberAbove(eth, BigInteger.valueOf(blockNumber))::verify;
  }
}
