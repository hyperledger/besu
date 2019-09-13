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
package tech.pegasys.pantheon.tests.acceptance.dsl.blockchain;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.blockchain.ExpectBeneficiary;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.blockchain.ExpectBlockNumberAbove;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.blockchain.ExpectMinimumBlockNumber;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.math.BigInteger;

public class Blockchain {

  private final EthTransactions eth;

  public Blockchain(final EthTransactions eth) {
    this.eth = eth;
  }

  public Condition blockNumberMustBeLatest(final Node node) {
    return new ExpectMinimumBlockNumber(eth, currentHeight(node));
  }

  public Condition beneficiaryEquals(final PantheonNode node) {
    return new ExpectBeneficiary(eth, node);
  }

  public Condition minimumHeight(final long blockNumber) {
    return new ExpectBlockNumberAbove(eth, BigInteger.valueOf(blockNumber));
  }

  public Condition reachesHeight(final PantheonNode node, final int blocksAheadOfLatest) {
    return new ExpectBlockNumberAbove(eth, futureHeight(node, blocksAheadOfLatest));
  }

  public Condition reachesHeight(
      final PantheonNode node, final int blocksAheadOfLatest, final int timeout) {
    return new ExpectBlockNumberAbove(eth, futureHeight(node, blocksAheadOfLatest), timeout);
  }

  private BigInteger futureHeight(final Node node, final int blocksAheadOfLatest) {
    return currentHeight(node).add(BigInteger.valueOf(blocksAheadOfLatest));
  }

  private BigInteger currentHeight(final Node node) {
    return node.execute(eth.blockNumber());
  }
}
