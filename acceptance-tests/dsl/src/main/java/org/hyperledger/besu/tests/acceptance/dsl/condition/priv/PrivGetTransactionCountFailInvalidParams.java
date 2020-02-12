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

import static org.assertj.core.api.AssertionsForClassTypes.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransactionCountTransaction;

import org.web3j.protocol.exceptions.ClientConnectionException;

public class PrivGetTransactionCountFailInvalidParams implements Condition {

  private final PrivGetTransactionCountTransaction privGetTransactionCountTransaction;

  public PrivGetTransactionCountFailInvalidParams(
      final PrivGetTransactionCountTransaction privGetTransactionCountTransaction) {
    this.privGetTransactionCountTransaction = privGetTransactionCountTransaction;
  }

  @Override
  public void verify(final Node node) {
    try {
      node.execute(privGetTransactionCountTransaction);
      failBecauseExceptionWasNotThrown(ClientConnectionException.class);
    } catch (final Exception e) {
      assertThat(e)
          .isInstanceOf(ClientConnectionException.class)
          .hasMessageContaining("400")
          .hasMessageContaining(INVALID_PARAMS.getMessage());
    }
  }
}
