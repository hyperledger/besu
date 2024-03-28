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
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
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
  private final Optional<Long> blockNumber;

  public AccountAdapter(final Account account) {
    this(account == null ? null : account.getAddress(), account, Optional.empty());
  }

  public AccountAdapter(final Account account, final Optional<Long> blockNumber) {
    this(account == null ? null : account.getAddress(), account, blockNumber);
  }

  public AccountAdapter(final Address address, final Account account) {
    this(address, account, Optional.empty());
  }

  public AccountAdapter(
      final Address address, final Account account, final Optional<Long> blockNumber) {
    this.account = Optional.ofNullable(account);
    this.address = address;
    this.blockNumber = blockNumber;
  }

  public Address getAddress() {
    return address;
  }

  public Wei getBalance() {
    return account.map(AccountState::getBalance).orElse(Wei.ZERO);
  }

  public Long getTransactionCount() {
    return account.map(AccountState::getNonce).orElse(0L);
  }

  public Bytes getCode(final DataFetchingEnvironment environment) {

    if (account.get() instanceof BonsaiAccount) {
      final BlockchainQueries query = getBlockchainQueries(environment);
      return query
          .getAndMapWorldState(
              blockNumber.orElse(query.headBlockNumber()),
              ws -> Optional.of(ws.get(account.get().getAddress()).getCode()))
          .get();
    } else {
      return account.map(AccountState::getCode).orElse(Bytes.EMPTY);
    }
  }

  public Bytes32 getStorage(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Bytes32 slot = environment.getArgument("slot");

    if (account.get() instanceof BonsaiAccount) {
      return query
          .getAndMapWorldState(
              blockNumber.orElse(query.headBlockNumber()),
              ws -> Optional.of((Bytes32) ws.get(address).getStorageValue(UInt256.fromBytes(slot))))
          .get();
    } else {
      return account
          .map(a -> (Bytes32) a.getStorageValue(UInt256.fromBytes(slot)))
          .orElse(Bytes32.ZERO);
    }
  }
}
