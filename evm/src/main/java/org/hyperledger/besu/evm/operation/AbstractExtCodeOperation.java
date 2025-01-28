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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.CodeDelegationHelper;

import org.apache.tuweni.bytes.Bytes;

/**
 * ExtCode* operations treat EOAs with delegated code differently than other operations. This
 * abstract class contains common methods for this behaviour.
 */
abstract class AbstractExtCodeOperation extends AbstractOperation {
  /**
   * Instantiates a new Abstract operation.
   *
   * @param opcode the opcode
   * @param name the name
   * @param stackItemsConsumed the stack items consumed
   * @param stackItemsProduced the stack items produced
   * @param gasCalculator the gas calculator
   */
  protected AbstractExtCodeOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
  }

  /**
   * Returns the code for standard accounts or a special designator for EOAs with delegated code
   *
   * @param account The account
   * @return the code or the special 7702 designator
   */
  protected Bytes getCode(final Account account) {
    if (account == null) {
      return Bytes.EMPTY;
    }

    return account.hasDelegatedCode()
        ? CodeDelegationHelper.getCodeDelegationForRead()
        : account.getCode();
  }

  /**
   * Returns the code hash for standard accounts or a special designator for EOAs with delegated
   * code
   *
   * @param account The account
   * @return the code hash or the hash of the special 7702 designator
   */
  protected Hash getCodeHash(final Account account) {
    if (account.hasDelegatedCode()) {
      return Hash.hash(CodeDelegationHelper.getCodeDelegationForRead());
    }

    return account.getCodeHash();
  }
}
