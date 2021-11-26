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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("unused") // reflected by GraphQL
public class AccountAdapter extends AdapterBase {
  private final Account account;

  public AccountAdapter(final Account account) {
    this.account = account;
  }

  public Optional<Address> getAddress() {
    return Optional.of(account.getAddress());
  }

  public Optional<Wei> getBalance() {
    return Optional.of(account.getBalance());
  }

  public Optional<Long> getTransactionCount() {
    return Optional.of(account.getNonce());
  }

  public Optional<Bytes> getCode() {
    return Optional.of(account.getCode());
  }

  public Optional<Bytes32> getStorage(final DataFetchingEnvironment environment) {
    final Bytes32 slot = environment.getArgument("slot");
    return Optional.of(account.getStorageValue(UInt256.fromBytes(slot)));
  }
}
