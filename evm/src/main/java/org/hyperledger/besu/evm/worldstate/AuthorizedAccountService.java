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
package org.hyperledger.besu.evm.worldstate;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AuthorizedCodeAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.account.MutableAuthorizedCodeAccount;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A service that manages the code injection of authorized accounts. */
public class AuthorizedAccountService {
  private final Map<Address, Address> authorizedAccounts = new HashMap<>();

  /** Creates a new AuthorizedAccountService. */
  public AuthorizedAccountService() {}

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
   * Processes the provided account, injecting the authorized code if authorized.
   *
   * @param worldUpdater the world updater to retrieve the code account.
   * @param originalAccount the account to process.
   * @param address the address of the account in case the provided account is null
   * @return the processed account, containing the authorized code if authorized.
   */
  public Account processAccount(
      final WorldUpdater worldUpdater, final Account originalAccount, final Address address) {
    if (!authorizedAccounts.containsKey(address)) {
      return originalAccount;
    }

    Account account = originalAccount;
    if (account == null) {
      account = worldUpdater.createAccount(address);
    }

    final Address authorizedCodeAddress = authorizedAccounts.get(address);
    final Account authorizedCodeAccount = worldUpdater.getOrCreate(authorizedCodeAddress);

    return new AuthorizedCodeAccount(account, authorizedCodeAccount.getCode());
  }

  /**
   * Processes the provided mutable account, injecting the authorized code if authorized.
   *
   * @param worldUpdater the world updater to retrieve the code account.
   * @param originalAccount the mutable account to process.
   * @param address the address of the account in case the provided account is null
   * @return the processed mutable account, containing the authorized code if authorized.
   */
  public MutableAccount processMutableAccount(
      final WorldUpdater worldUpdater,
      final MutableAccount originalAccount,
      final Address address) {
    if (!authorizedAccounts.containsKey(address)) {
      return originalAccount;
    }

    MutableAccount account = originalAccount;
    if (account == null) {
      account = worldUpdater.createAccount(address);
    }

    final Address authorizedCodeAddress = authorizedAccounts.get(address);
    final Account authorizedCodeAccount = worldUpdater.getOrCreate(authorizedCodeAddress);

    return new MutableAuthorizedCodeAccount(account, authorizedCodeAccount.getCode());
  }
}
