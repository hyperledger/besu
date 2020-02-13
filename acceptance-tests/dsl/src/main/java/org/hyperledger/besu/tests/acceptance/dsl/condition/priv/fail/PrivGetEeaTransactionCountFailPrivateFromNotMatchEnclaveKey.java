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
package org.hyperledger.besu.tests.acceptance.dsl.condition.priv.fail;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetEeaTransactionCountTransaction;

import org.web3j.protocol.exceptions.ClientConnectionException;

public class PrivGetEeaTransactionCountFailPrivateFromNotMatchEnclaveKey implements Condition {

  private final PrivGetEeaTransactionCountTransaction privGetEeaTransactionCountTransaction;

  public PrivGetEeaTransactionCountFailPrivateFromNotMatchEnclaveKey(
      final PrivGetEeaTransactionCountTransaction privGetEeaTransactionCountTransaction) {
    this.privGetEeaTransactionCountTransaction = privGetEeaTransactionCountTransaction;
  }

  @Override
  public void verify(final Node node) {
    try {
      node.execute(privGetEeaTransactionCountTransaction);
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(ClientConnectionException.class);
    }
  }
}
