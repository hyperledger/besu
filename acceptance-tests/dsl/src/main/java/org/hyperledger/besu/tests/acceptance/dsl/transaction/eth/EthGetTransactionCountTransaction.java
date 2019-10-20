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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.eth;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

public class EthGetTransactionCountTransaction implements Transaction<BigInteger> {

  private final String accountAddress;

  public EthGetTransactionCountTransaction(final String accountAddress) {
    this.accountAddress = accountAddress;
  }

  @Override
  public BigInteger execute(final NodeRequests node) {
    try {
      EthGetTransactionCount result =
          node.eth()
              .ethGetTransactionCount(accountAddress, DefaultBlockParameterName.LATEST)
              .send();
      assertThat(result).isNotNull();
      return result.getTransactionCount();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
