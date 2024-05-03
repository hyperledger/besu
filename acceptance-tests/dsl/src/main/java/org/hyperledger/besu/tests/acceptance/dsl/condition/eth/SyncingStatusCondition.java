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
package org.hyperledger.besu.tests.acceptance.dsl.condition.eth;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthSyncingTransaction;

public class SyncingStatusCondition implements Condition {

  private final EthSyncingTransaction transaction;
  private final boolean syncingMiningStatus;

  public SyncingStatusCondition(
      final EthSyncingTransaction transaction, final boolean syncingStatus) {
    this.transaction = transaction;
    this.syncingMiningStatus = syncingStatus;
  }

  @Override
  public void verify(final Node node) {
    WaitUtils.waitFor(
        10, () -> assertThat(node.execute(transaction)).isEqualTo(syncingMiningStatus));
  }
}
