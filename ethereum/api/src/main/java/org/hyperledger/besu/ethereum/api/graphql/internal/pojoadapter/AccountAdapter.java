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
import org.hyperledger.besu.evm.account.AccountState;

import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("unused") // reflected by GraphQL
public class AccountAdapter extends AdapterBase {

  private final Optional<Account> account;
  private final Address address;

  public AccountAdapter(final Account account) {
    this(account == null ? null : account.getAddress(), account);
  }

  public AccountAdapter(final Address address, final Account account) {
    this.account = Optional.ofNullable(account);
    this.address = address;
  }

  public Optional<Address> getAddress() {
    return Optional.of(address);
  }

  public Optional<Wei> getBalance() {
    return account.map(AccountState::getBalance).or(() -> Optional.of(Wei.ZERO));
  }

  public Optional<Long> getTransactionCount() {
    return account.map(AccountState::getNonce).or(() -> Optional.of(0L));
  }

  public Optional<Bytes> getCode() {
    return account.map(AccountState::getCode);
  }

  public Optional<Bytes32> getStorage(final DataFetchingEnvironment environment) {
    final Bytes32 slot = environment.getArgument("slot");
    return account.map(account -> account.getStorageValue(UInt256.fromBytes(slot)));
  }
}
