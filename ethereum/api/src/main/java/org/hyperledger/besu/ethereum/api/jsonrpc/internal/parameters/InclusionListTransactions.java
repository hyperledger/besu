/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public final class InclusionListTransactions {
  private final Set<Transaction> transactions;
  private final Bytes32 checksum;

  public InclusionListTransactions(final Set<Transaction> transactions) {
    this.transactions = transactions;
    this.checksum =
        keccak256(
            Bytes.wrap(
                transactions.stream().map(Transaction::getHash).map(Hash::getBytes).toList()));
  }

  public Set<Transaction> getTransactions() {
    return transactions;
  }

  public Bytes32 getChecksum() {
    return checksum;
  }
}
