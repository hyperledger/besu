/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.frame;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AuthorizedCodeAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.account.MutableAuthorizedCodeAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;

/**
 * A service that provides a mutable view of the world state.
 *
 * <p>This service is a wrapper around a {@link WorldUpdater} that provides additional features such
 * as the ability to authorize accounts to load their code into an EOA account.
 */
public class WorldUpdaterService {
  private final WorldUpdater worldUpdater;
  private final Map<Address, Address> authorizedAccounts = new HashMap<>();
  private final Map<Address, Bytes> authorizedCodes = new HashMap<>();

  /**
   * Creates a new world updater service.
   *
   * @param worldUpdater the underlying world updater.
   */
  public WorldUpdaterService(final WorldUpdater worldUpdater) {
    this.worldUpdater = worldUpdater;
  }

  /**
   * Returns the underlying world updater.
   *
   * @return the underlying world updater.
   */
  public WorldUpdater getWorldUpdater() {
    return worldUpdater;
  }

  /**
   * Authorizes to load the code of authorizedAccount into the authorizer account.
   *
   * @param authorizer the address that gives the authorization.
   * @param authorizedAccount the address of the account which code will be loaded.
   */
  public void addAuthorizedAccount(final Address authorizer, final Address authorizedAccount) {
    authorizedAccounts.put(authorizer, authorizedAccount);
  }

  /**
   * Return all the authorities that have given their authorization to load the code of another
   * account.
   *
   * @return the set of authorities.
   */
  public Set<Address> getAuthorities() {
    return authorizedAccounts.keySet();
  }

  /** Resets all the authorized accounts. */
  public void resetAuthorities() {
    authorizedAccounts.clear();
  }

  /**
   * Checks if the provided address has set an authorized to load code into an EOA account.
   *
   * @param authority the address to check.
   * @return {@code true} if the address has been authorized, {@code false} otherwise.
   */
  public boolean hasAuthorization(final Address authority) {
    return authorizedAccounts.containsKey(authority);
  }

  /**
   * Get an account provided its address.
   *
   * @param address the address of the account to retrieve.
   * @return the {@link Account} corresponding to {@code address} or {@code null} if there is no
   *     such account.
   */
  public Account get(final Address address) {
    Account account = worldUpdater.get(address);

    if (!authorizedAccounts.containsKey(address)) {
      return account;
    }

    if (account == null) {
      account = worldUpdater.createAccount(address);
    }

    final Address authorizedCodeAddress = authorizedAccounts.get(address);
    if (authorizedCodes.containsKey(authorizedCodeAddress)) {
      return new AuthorizedCodeAccount(account, authorizedCodes.get(authorizedCodeAddress));
    }

    final Account authorizedCodeAccount = worldUpdater.getOrCreate(authorizedCodeAddress);

    // we don't cache empty code, because it can change when a contract is deployed there
    if (!authorizedCodeAccount.getCode().equals(Bytes.EMPTY)) {
      authorizedCodes.put(authorizedCodeAddress, authorizedCodeAccount.getCode());
    }

    return new AuthorizedCodeAccount(account, authorizedCodeAccount.getCode());
  }

  /**
   * Creates a new account, or reset it (that is, act as if it was deleted and created anew) if it
   * already exists.
   *
   * <p>After this call, the account will exists and will have the provided nonce and balance. The
   * code and storage will be empty.
   *
   * @param address the address of the account to create (or reset).
   * @param nonce the nonce for created/reset account.
   * @param balance the balance for created/reset account.
   * @return the account {@code address}, which will have nonce {@code nonce}, balance {@code
   *     balance} and empty code and storage.
   */
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    final MutableAccount account = worldUpdater.createAccount(address, nonce, balance);

    if (!authorizedAccounts.containsKey(address)) {
      return account;
    }

    return createMutableAuthorizedCodeAccount(account);
  }

  /**
   * Creates a new account, or reset it (that is, act as if it was deleted and created anew) if it
   * already exists.
   *
   * <p>This call is equivalent to {@link #createAccount(Address, long, Wei)} but defaults both the
   * nonce and balance to zero.
   *
   * @param address the address of the account to create (or reset).
   * @return the account {@code address}, which will have 0 for the nonce and balance and empty code
   *     and storage.
   */
  public MutableAccount createAccount(final Address address) {
    final MutableAccount account = worldUpdater.createAccount(address);

    if (!authorizedAccounts.containsKey(address)) {
      return account;
    }

    return createMutableAuthorizedCodeAccount(account);
  }

  /**
   * Retrieves the provided account if it exists, or create it if it doesn't.
   *
   * @param address the address of the account.
   * @return the account {@code address}. If that account exists, it is returned as if by {@link
   *     #getAccount(Address)}, otherwise, it is created and returned as if by {@link
   *     #createAccount(Address)} (and thus all his fields will be zero/empty).
   */
  public MutableAccount getOrCreate(final Address address) {
    final MutableAccount account = worldUpdater.getOrCreate(address);

    if (!authorizedAccounts.containsKey(address)) {
      return account;
    }

    return createMutableAuthorizedCodeAccount(account);
  }

  /**
   * Retrieves the provided account, returning a modifiable object (whose updates are accumulated by
   * the world updater).
   *
   * @param address the address of the account.
   * @return the account {@code address}, or {@code null} if the account does not exist.
   */
  public MutableAccount getAccount(final Address address) {
    final MutableAccount account = worldUpdater.getAccount(address);

    if (!authorizedAccounts.containsKey(address)) {
      return account;
    }

    return createMutableAuthorizedCodeAccount(account);
  }

  /**
   * Returns the accounts that have been touched within the scope of the world updater.
   *
   * @return the accounts that have been touched within the scope of the world updater
   */
  public Collection<? extends Account> getTouchedAccounts() {
    return worldUpdater.getTouchedAccounts();
  }

  /** Removes the changes that were made to the world updater. */
  public void revert() {
    worldUpdater.revert();
  }

  /**
   * Retrieves the senders account, returning a modifiable object (whose updates are accumulated by
   * this updater).
   *
   * @param frame the current message frame.
   * @return the account {@code address}, or {@code null} if the account does not exist.
   */
  public MutableAccount getSenderAccount(final MessageFrame frame) {
    return worldUpdater.getSenderAccount(frame);
  }

  /**
   * Deletes the provided account.
   *
   * @param address the address of the account to delete. If that account doesn't exists prior to
   *     this call, this is a no-op.
   */
  public void deleteAccount(final Address address) {
    worldUpdater.deleteAccount(address);
  }

  /**
   * Returns the account addresses that have been deleted within the scope of this updater.
   *
   * @return the account addresses that have been deleted within the scope of this updater
   */
  public Collection<Address> getDeletedAccountAddresses() {
    return worldUpdater.getDeletedAccountAddresses();
  }

  /** Commits the changes made to the world updater to the underlying {@link WorldView} */
  public void commit() {
    worldUpdater.commit();
  }

  /**
   * Creates an updater for this mutable world view.
   *
   * @return a new updater for this mutable world view. On commit, change made to this updater will
   *     become visible on this view.
   */
  public WorldUpdater updater() {
    return worldUpdater.updater();
  }

  /**
   * The parent updater (if it exists).
   *
   * @return The parent WorldUpdater if this wraps another one, empty otherwise
   */
  public Optional<WorldUpdater> parentUpdater() {
    return worldUpdater.parentUpdater();
  }

  private MutableAccount createMutableAuthorizedCodeAccount(final MutableAccount account) {
    final Address authorizedCodeAddress = authorizedAccounts.get(account.getAddress());
    if (!authorizedCodes.containsKey(authorizedCodeAddress)) {
      final Account authorizedCodeAccount = worldUpdater.getOrCreate(authorizedCodeAddress);
      authorizedCodes.put(authorizedCodeAddress, authorizedCodeAccount.getCode());
    }

    return new MutableAuthorizedCodeAccount(account, authorizedCodes.get(authorizedCodeAddress));
  }
}
