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

import static org.hyperledger.besu.evm.code.CodeV0.EMPTY_CODE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.Eip7928AccessList;

import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;

/** Helper class for 7702 delegated code interactions */
public class CodeDelegationHelper {
  /**
   * Represents a target for code delegation, containing the target address and the code to execute
   *
   * @param address the address of the target account
   * @param code the code to execute at the target address
   */
  public record Target(Address address, Code code) {}

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
   * return the target address from the byte code. The method assumes that the byte code is a valid
   * according to EIP-7702
   *
   * @param code the 7702 byte code in the form CODE_DELEGATION_PREFIX + target address
   * @return the address of the target
   */
  public static Address getTargetAddress(final Bytes code) {
    return Address.wrap(code.slice(CODE_DELEGATION_PREFIX.size()));
  }

  /**
   * Returns the target account of the delegated code.
   *
   * @param worldUpdater the world updater.
   * @param isPrecompile function to check if an address belongs to a precompile account.
   * @param account the account which has a code delegation.
   * @param eip7928AccessList data structure to record account and storage accesses.
   * @return the target address and its code. Throws an exception if the account does not have a
   *     code delegation or if the account is null.
   */
  public static Target getTarget(
      final WorldUpdater worldUpdater,
      final Predicate<Address> isPrecompile,
      final Account account,
      final Optional<? extends Eip7928AccessList> eip7928AccessList)
      throws IllegalArgumentException {
    if (account == null) {
      throw new IllegalArgumentException("Account must not be null.");
    }

    if (!hasCodeDelegation(account.getCode())) {
      throw new IllegalArgumentException("Account does not have code delegation.");
    }

    final Address targetAddress =
        Address.wrap(account.getCode().slice(CODE_DELEGATION_PREFIX.size()));

    return new Target(
        targetAddress,
        processTargetCode(worldUpdater, isPrecompile, targetAddress, eip7928AccessList));
  }

  private static Code processTargetCode(
      final WorldUpdater worldUpdater,
      final Predicate<Address> isPrecompile,
      final Address targetAddress,
      final Optional<? extends Eip7928AccessList> eip7928AccessList) {
    if (targetAddress == null) {
      return EMPTY_CODE;
    }

    final Account targetAccount = worldUpdater.get(targetAddress);
    eip7928AccessList.ifPresent(t -> t.addAccount(targetAddress));

    if (targetAccount == null || isPrecompile.test(targetAddress)) {
      return EMPTY_CODE;
    }

    // Bonsai accounts may have a fully cached code, so we use that one
    if (targetAccount.getCodeCache() != null) {
      return targetAccount.getOrCreateCachedCode();
    }

    return targetAccount.getOrCreateCachedCode();
  }
}
