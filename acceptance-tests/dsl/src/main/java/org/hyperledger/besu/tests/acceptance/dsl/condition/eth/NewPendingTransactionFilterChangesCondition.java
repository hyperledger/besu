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
package org.hyperledger.besu.tests.acceptance.dsl.condition.eth;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthFilterChangesTransaction;

import java.util.List;

import org.web3j.protocol.core.methods.response.EthLog;

public class NewPendingTransactionFilterChangesCondition implements Condition {

  private final EthFilterChangesTransaction filterChanges;
  private final List<String> transactionHashes;

  public NewPendingTransactionFilterChangesCondition(
      final EthFilterChangesTransaction filterChanges, final List<String> transactionHashes) {

    this.filterChanges = filterChanges;
    this.transactionHashes = transactionHashes;
  }

  @Override
  public void verify(final Node node) {
    WaitUtils.waitFor(
        () -> {
          final EthLog response = node.execute(filterChanges);
          assertThat(response).isNotNull();
          assertThat(response.getResult().size()).isEqualTo(transactionHashes.size());
          for (int i = 0; i < transactionHashes.size(); ++i) {
            assertThat(response.getLogs().get(0).get()).isEqualTo(transactionHashes.get(0));
          }
        });
  }
}
