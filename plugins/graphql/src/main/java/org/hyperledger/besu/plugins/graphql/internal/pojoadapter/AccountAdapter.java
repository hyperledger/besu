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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.plugins.graphql.internal.query.ChainQueries;

import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The AccountAdapter class extends the AdapterBase class. It provides methods to get the account
 * details such as address, balance, transaction count, code, and storage.
 *
 * <p>An account must not be used after the world state it was read from is closed, so the adapter
 * keeps a snapshot of the balance and nonce, and reads the code and storage from a world state it
 * opens again: the state of the block the account was read at on a Forest world state, and the
 * state of the block number given at construction, or else of the chain head, on a Bonsai world
 * state. That is what Besu has always returned.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class AccountAdapter extends AdapterBase {

  private record AccountSnapshot(Address address, Wei balance, long nonce) {}

  private final Optional<AccountSnapshot> account;
  private final Address address;
  private final Optional<Hash> stateBlockHash;
  private final Optional<Long> blockNumber;

  /**
   * Constructs a new AccountAdapter with the given Account.
   *
   * @param account the Account to be adapted, may be null
   * @param stateBlockHash the hash of the block whose world state the account was read from
   */
  public AccountAdapter(final Account account, final Hash stateBlockHash) {
    this(account == null ? null : account.getAddress(), account, stateBlockHash, Optional.empty());
  }

  /**
   * Constructs a new AccountAdapter with the given Account and block number.
   *
   * @param account the Account to be adapted, may be null
   * @param stateBlockHash the hash of the block whose world state the account was read from
   * @param blockNumber the block number associated with the account
   */
  public AccountAdapter(
      final Account account, final Hash stateBlockHash, final Optional<Long> blockNumber) {
    this(account == null ? null : account.getAddress(), account, stateBlockHash, blockNumber);
  }

  /**
   * Constructs a new AccountAdapter with the given address and Account.
   *
   * @param address the address of the account
   * @param account the Account to be adapted, may be null
   * @param stateBlockHash the hash of the block whose world state the account was read from
   */
  public AccountAdapter(final Address address, final Account account, final Hash stateBlockHash) {
    this(address, account, stateBlockHash, Optional.empty());
  }

  /**
   * Constructs a new AccountAdapter with the given address, Account, and block number.
   *
   * @param address the address of the account
   * @param account the Account to be adapted, may be null
   * @param stateBlockHash the hash of the block whose world state the account was read from
   * @param blockNumber the block number associated with the account
   */
  public AccountAdapter(
      final Address address,
      final Account account,
      final Hash stateBlockHash,
      final Optional<Long> blockNumber) {
    this.account =
        Optional.ofNullable(account)
            .map(a -> new AccountSnapshot(a.getAddress(), a.getBalance(), a.getNonce()));
    this.address = address;
    this.stateBlockHash = Optional.ofNullable(stateBlockHash);
    this.blockNumber = blockNumber;
  }

  /**
   * Returns the address of the account.
   *
   * @return the address of the account
   */
  public Address getAddress() {
    return address;
  }

  /**
   * Returns the balance of the account.
   *
   * @return the balance of the account
   */
  public Wei getBalance() {
    return account.map(AccountSnapshot::balance).orElse(Wei.ZERO);
  }

  /**
   * Returns the transaction count of the account.
   *
   * @return the transaction count of the account
   */
  public Long getTransactionCount() {
    return account.map(AccountSnapshot::nonce).orElse(0L);
  }

  /**
   * Returns the code of the account.
   *
   * @param environment the DataFetchingEnvironment
   * @return the code of the account
   */
  public Bytes getCode(final DataFetchingEnvironment environment) {
    final Address accountAddress = account.get().address();
    final ChainQueries query = getBlockchainQueries(environment);
    if (query.isBonsaiWorldState()) {
      return query
          .getAndMapWorldState(
              blockNumber.orElseGet(query::headBlockNumber),
              ws -> Optional.of(ws.get(accountAddress).getCode()))
          .get();
    } else {
      return query
          .getAndMapWorldState(
              stateBlockHash.get(), ws -> Optional.of(ws.get(accountAddress).getCode()))
          .get();
    }
  }

  /**
   * Returns the storage of the account.
   *
   * @param environment the DataFetchingEnvironment
   * @return the storage of the account
   */
  public Bytes32 getStorage(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final Bytes32 slot = environment.getArgument("slot");

    final Address accountAddress = account.get().address();
    if (query.isBonsaiWorldState()) {
      return query
          .getAndMapWorldState(
              blockNumber.orElseGet(query::headBlockNumber),
              ws -> Optional.of((Bytes32) ws.get(address).getStorageValue(UInt256.fromBytes(slot))))
          .get();
    } else {
      return query
          .getAndMapWorldState(
              stateBlockHash.get(),
              ws ->
                  Optional.of(
                      (Bytes32) ws.get(accountAddress).getStorageValue(UInt256.fromBytes(slot))))
          .get();
    }
  }
}
