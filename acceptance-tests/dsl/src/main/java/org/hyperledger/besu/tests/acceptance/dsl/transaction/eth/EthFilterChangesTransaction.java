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

import org.web3j.protocol.core.methods.response.EthLog;

public class EthFilterChangesTransaction implements Transaction<EthLog> {
  private final BigInteger filterId;

  public EthFilterChangesTransaction(final BigInteger filterId) {
    this.filterId = filterId;
  }

  @Override
  public EthLog execute(final NodeRequests node) {
    try {
      final EthLog response = node.eth().ethGetFilterChanges(filterId).send();
      assertThat(response.getLogs()).isNotNull();
      return response;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
