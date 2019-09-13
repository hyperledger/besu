/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

public class TransferTransactionSet implements Transaction<List<Hash>> {

  private final List<TransferTransaction> transactions;

  public TransferTransactionSet(final List<TransferTransaction> transactions) {
    this.transactions = transactions;
  }

  @Override
  public List<Hash> execute(final NodeRequests node) {
    final List<Hash> hashes = new ArrayList<>();

    for (final TransferTransaction transaction : transactions) {
      hashes.add(transaction.execute(node));
    }

    return hashes;
  }
}
