/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.evm.account.CodeDelegationAccount;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** Helper class for 7702 delegated code interactions */
public class CodeDelegationHelper {
  /** The prefix that is used to identify delegated code */
  public static final Bytes CODE_DELEGATION_PREFIX = Bytes.fromHexString("ef0100");

  /** The size of the delegated code */
  public static final int DELEGATED_CODE_SIZE = CODE_DELEGATION_PREFIX.size() + Address.SIZE;

  /** create a new DelegateCodeHelper */
  public CodeDelegationHelper() {
    // empty
  }

  /**
   * Returns if the provided code is delegated code.
   *
   * @param code the code to check.
   * @return {@code true} if the code is delegated code, {@code false} otherwise.
   */
  public static boolean hasCodeDelegation(final Bytes code) {
    return code != null
        && code.size() == DELEGATED_CODE_SIZE
        && code.slice(0, CODE_DELEGATION_PREFIX.size()).equals(CODE_DELEGATION_PREFIX);
  }

  /**
   * Returns the target account of the delegated code.
   *
   * @param worldUpdater the world updater.
   * @param gasCalculator the gas calculator.
   * @param account the account which has a code delegation.
   * @return the target account of the delegated code, throws IllegalArgumentException if account is
   *     null or doesn't have code delegation.
   */
  public static CodeDelegationAccount getTargetAccount(
      final WorldUpdater worldUpdater, final GasCalculator gasCalculator, final Account account)
      throws IllegalArgumentException {
    if (account == null) {
      throw new IllegalArgumentException("Account must not be null.");
    }

    if (!hasCodeDelegation(account.getCode())) {
      throw new IllegalArgumentException("Account does not have code delegation.");
    }

    final Address targetAddress =
        Address.wrap(account.getCode().slice(CODE_DELEGATION_PREFIX.size()));

    final Bytes targetCode = processTargetCode(worldUpdater, gasCalculator, targetAddress);

    return new CodeDelegationAccount(account, targetAddress, targetCode);
  }

  private static Bytes processTargetCode(
      final WorldUpdater worldUpdater,
      final GasCalculator gasCalculator,
      final Address targetAddress) {
    if (targetAddress == null) {
      return Bytes.EMPTY;
    }

    final Account targetAccount = worldUpdater.get(targetAddress);

    if (targetAccount == null || gasCalculator.isPrecompile(targetAddress)) {
      return Bytes.EMPTY;
    }
    return targetAccount.getCode();
  }
}
