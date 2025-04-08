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

import static org.hyperledger.besu.evm.worldstate.CodeDelegationHelper.CODE_DELEGATION_PREFIX;
import static org.hyperledger.besu.evm.worldstate.CodeDelegationHelper.hasCodeDelegation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;

import org.apache.tuweni.bytes.Bytes;

/** A service that manages the code injection of delegated code. */
public class CodeDelegationService {
  /** Creates a new CodeDelegationService. */
  public CodeDelegationService() {
    // empty
  }

  /**
   * Process the delegated code authorization. It will set the code to 0x ef0100 + delegated code
   * address. If the address is 0, it will set the code to empty.
   *
   * @param account the account to which the delegated code is added.
   * @param codeDelegationAddress the address of the target of the authorization.
   */
  public void processCodeDelegation(
      final MutableAccount account, final Address codeDelegationAddress) {
    // code delegation to zero address removes any delegated code
    if (codeDelegationAddress.equals(Address.ZERO)) {
      account.setCode(Bytes.EMPTY);
      return;
    }

    account.setCode(Bytes.concatenate(CODE_DELEGATION_PREFIX, codeDelegationAddress));
  }

  /**
   * Returns true if the provided account has either no code set or has already delegated code.
   *
   * @param account the account to check.
   * @return {@code true} if the account can set delegated code, {@code false} otherwise.
   */
  public boolean canSetCodeDelegation(final Account account) {
    return account != null && (account.getCode().isEmpty() || hasCodeDelegation(account.getCode()));
  }
}
