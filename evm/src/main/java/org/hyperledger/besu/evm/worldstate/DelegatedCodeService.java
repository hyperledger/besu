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
import org.hyperledger.besu.evm.account.DelegatedCodeAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.account.MutableDelegatedCodeAccount;

import org.apache.tuweni.bytes.Bytes;

/** A service that manages the code injection of delegated code. */
public class DelegatedCodeService {
  private static final Bytes DELEGATED_CODE_PREFIX = Bytes.fromHexString("ef0100");
  private static final int DELEGATED_CODE_SIZE = DELEGATED_CODE_PREFIX.size() + Address.SIZE;

  /** Creates a new DelegatedCodeService. */
  public DelegatedCodeService() {}

  /**
   * Add the delegated code to the given account.
   *
   * @param account the account to which the delegated code is added.
   * @param delegatedCodeAddress the address of the delegated code.
   */
  public void addDelegatedCode(final MutableAccount account, final Address delegatedCodeAddress) {
    account.setCode(Bytes.concatenate(DELEGATED_CODE_PREFIX, delegatedCodeAddress));
  }

  /**
   * Returns if the provided account has either no code set or has already delegated code.
   *
   * @param account the account to check.
   * @return {@code true} if the account can set delegated code, {@code false} otherwise.
   */
  public boolean canSetDelegatedCode(final Account account) {
    return account.getCode().isEmpty() || hasDelegatedCode(account.getUnprocessedCode());
  }

  /**
   * Processes the provided account, resolving the code if delegated.
   *
   * @param worldUpdater the world updater to retrieve the delegated code.
   * @param account the account to process.
   * @return the processed account, containing the delegated code if set, the unmodified account
   *     otherwise.
   */
  public Account processAccount(final WorldUpdater worldUpdater, final Account account) {
    if (account == null || !hasDelegatedCode(account.getCode())) {
      return account;
    }

    return new DelegatedCodeAccount(
        worldUpdater, account, resolveDelegatedAddress(account.getCode()));
  }

  /**
   * Processes the provided mutable account, resolving the code if delegated.
   *
   * @param worldUpdater the world updater to retrieve the delegated code.
   * @param account the mutable account to process.
   * @return the processed mutable account, containing the delegated code if set, the unmodified
   *     mutable account otherwise.
   */
  public MutableAccount processMutableAccount(
      final WorldUpdater worldUpdater, final MutableAccount account) {
    if (account == null || !hasDelegatedCode(account.getCode())) {
      return account;
    }

    return new MutableDelegatedCodeAccount(
        worldUpdater, account, resolveDelegatedAddress(account.getCode()));
  }

  /**
   * Returns if the provided code is delegated code.
   *
   * @param code the code to check.
   * @return {@code true} if the code is delegated code, {@code false} otherwise.
   */
  public static boolean hasDelegatedCode(final Bytes code) {
    return code != null
        && code.size() == DELEGATED_CODE_SIZE
        && code.slice(0, DELEGATED_CODE_PREFIX.size()).equals(DELEGATED_CODE_PREFIX);
  }

  private Address resolveDelegatedAddress(final Bytes code) {
    return Address.wrap(code.slice(DELEGATED_CODE_PREFIX.size()));
  }
}
