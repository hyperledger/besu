/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm;

import org.hyperledger.besu.plugin.data.Account;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * The state interface used by the EVM to access/modify chain state such as {@link Account} and
 * {@link EVMAccountState}
 */
public interface EVMWorldState {

  /**
   * Gets account from State. EVM is executing this call everytime it needs to access a
   * contract/address, f.e getting recipient address multiple times during 1 contract executions
   *
   * @param address Address for which to get the Account
   * @return the {@link Account} corresponding to {@code address} or {@code null} if there is no *
   *     such account.
   */
  Account getAccount(Address address);

  /**
   * Gets account from State if it exists, or a blank account if the account is
   * empty/dead/non-existant and the account can be created.
   *
   * @param address Address for which to get the Account
   * @return Account. Empty if the account cannot be created
   */
  Optional<Account> getOrCreateAccount(Address address);

  /**
   * //FIXME re-doc Returns a new instance of {@link EVMAccountState} for the specified account The
   * account storage map is accessed during Contract execution for reading contract storage ({@link
   * org.apache.tuweni.units.bigints.UInt256} key/value pairs and once the transaction executes, the
   * buffered changes in the {@link org.hyperledger.besu.ethereum.core.AbstractWorldUpdater} are
   * committed to the {@link DefaultMutableWorldState} where the modified {@link EVMAccountState}
   * are updated
   *
   * @param address the address to get storage map for
   * @return AccountStorageMap of the account
   */
  EVMAccountState getAccountState(Address address);

  /**
   * Provisionally updates the Balance of a given {@link Address}.
   *
   * @param address the address to update
   * @param balance the new balance of the account
   */
  void updateAccountBalance(Address address, Wei balance);

  /**
   * Provisionally updates the nonce of a given {@link Address}.
   *
   * @param address the address to update
   * @param nonce the new nonce of the account
   */
  void updateAccountNonce(Address address, long nonce);

  /**
   * Provisionally updates the code for a given {@link Account}
   *
   * @param address the address to update
   * @param code the new code for the account
   */
  void updateAccountCode(Address address, Bytes code);

  /**
   * Retrieves the smart contract code for a given {@link Address}
   *
   * @param address the address to get the code for
   * @return Bytes of the contract code. Must return `Bytes.EMPTY` if the address does not have
   *     contract code
   */
  Bytes getCode(Address address);

  /**
   * Provisionally removes a given address from state. Clears the contract code and storage of the
   * account as-well
   *
   * @param address the address to be removed.
   */
  void deleteAccount(Address address);

  /**
   * Provisionally clears the storage for a given address in state. Called when new contract is
   * being deployed or already existing contract is being destroyed
   *
   * @param address the address to get their storage cleared
   */
  void deleteAccountState(Address address);

  /**
   * Commits the performed changes into the State. For a nested world state the state will be
   * wrapped into the parent state. If this is not a nested state this will commit the changes to
   * the underlying storage.
   */
  void commit();

  /**
   * Rolls back all changes that would be performed in this update and does any implementation
   * specific cleanup. World state may be reu-sed after this operation.
   */
  void rollback();

  /**
   * Creates a new nested world state. When the returned world state is committed it will be
   * reflected in this world state.
   *
   * @return The new child world state
   */
  EVMWorldState createNestedWorldState();

  /**
   * The accounts modified in this world state.
   *
   * @return The accounts modified in this world state.
   */
  Collection<? extends Account> getUpdatedAccounts();
}
