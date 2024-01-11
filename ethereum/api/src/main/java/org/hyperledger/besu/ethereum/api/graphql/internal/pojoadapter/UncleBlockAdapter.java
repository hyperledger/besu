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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("unused") // reflected by GraphQL
class UncleBlockAdapter extends BlockAdapterBase {

  UncleBlockAdapter(final BlockHeader uncleHeader) {
    super(uncleHeader);
  }

  public Optional<Integer> getTransactionCount() {
    return Optional.of(0);
  }

  public UInt256 getTotalDifficulty() {
    return UInt256.ZERO;
  }

  public Optional<Integer> getOmmerCount() {
    return Optional.empty();
  }

  public List<NormalBlockAdapter> getOmmers() {
    return new ArrayList<>();
  }

  public Optional<NormalBlockAdapter> getOmmerAt() {
    return Optional.empty();
  }

  public List<TransactionAdapter> getTransactions() {
    return new ArrayList<>();
  }

  public Optional<TransactionAdapter> getTransactionAt() {
    return Optional.empty();
  }
}
