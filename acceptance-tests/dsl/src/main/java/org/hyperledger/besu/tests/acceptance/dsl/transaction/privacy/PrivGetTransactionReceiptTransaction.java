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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;

public class PrivGetTransactionReceiptTransaction
    implements Transaction<PrivateTransactionReceipt> {

  private final Hash transaction;

  public PrivGetTransactionReceiptTransaction(final Hash transaction) {
    this.transaction = transaction;
  }

  @Override
  public PrivateTransactionReceipt execute(final NodeRequests node) {
    try {
      final PrivacyRequestFactory.GetTransactionReceiptResponse result =
          node.privacy().privGetTransactionReceipt(transaction).send();
      assertThat(result).isNotNull();
      return result.getResult();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
