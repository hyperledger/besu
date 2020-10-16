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

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import org.assertj.core.api.Assertions;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class ExpectJsonRpcError implements Condition {

  private final Transaction<?> transaction;
  private final JsonRpcError error;

  public ExpectJsonRpcError(final Transaction<?> transaction, final JsonRpcError error) {
    this.transaction = transaction;
    this.error = error;
  }

  @Override
  public void verify(final Node node) {
    try {
      node.execute(transaction);
      failBecauseExceptionWasNotThrown(ClientConnectionException.class);
    } catch (final Exception e) {
      Assertions.assertThat(e)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining(error.getMessage());
    }
  }
}
