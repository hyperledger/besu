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
package org.hyperledger.besu.tests.acceptance.dsl.condition.priv;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransactionReceiptTransaction;

import org.assertj.core.api.Assertions;

public class PrivGetTransactionReceiptSuccess implements Condition {

  private final PrivGetTransactionReceiptTransaction privGetTransactionCountTransaction;

  public PrivGetTransactionReceiptSuccess(
      final PrivGetTransactionReceiptTransaction privGetTransactionCountTransaction) {
    this.privGetTransactionCountTransaction = privGetTransactionCountTransaction;
  }

  @Override
  public void verify(final Node node) {
    WaitUtils.waitFor(
        () -> Assertions.assertThat(node.execute(privGetTransactionCountTransaction)).isNotNull());
  }
}
