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
package org.hyperledger.besu.tests.acceptance.dsl.condition.txpool;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolTransactions;

import java.util.List;
import java.util.stream.Collectors;

public class TxPoolConditions {

  private final TxPoolTransactions txPoolTransactions;

  public TxPoolConditions(final TxPoolTransactions txPoolTransactions) {
    this.txPoolTransactions = txPoolTransactions;
  }

  public Condition inTransactionPool(final Hash txHash) {
    return node ->
        WaitUtils.waitFor(() -> assertThat(nodeTransactionHashes(node)).contains(txHash));
  }

  public Condition notInTransactionPool(final Hash txHash) {
    return node -> assertThat(nodeTransactionHashes(node)).doesNotContain(txHash);
  }

  private List<Hash> nodeTransactionHashes(final Node node) {
    return node.execute(txPoolTransactions.getTxPoolContents()).stream()
        .map((txInfo) -> Hash.fromHexString(txInfo.get("hash")))
        .collect(Collectors.toList());
  }
}
