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
package org.hyperledger.besu.evm.account;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

abstract class AbstractDelegatedCodeAccount implements Account {
  private final WorldUpdater worldUpdater;
  private final GasCalculator gasCalculator;

  /** The address of the account that has delegated code to be loaded into it. */
  protected final Address delegatedCodeAddress;

  protected AbstractDelegatedCodeAccount(
      final WorldUpdater worldUpdater,
      final Address delegatedCodeAddress,
      final GasCalculator gasCalculator) {
    this.worldUpdater = worldUpdater;
    this.gasCalculator = gasCalculator;
    this.delegatedCodeAddress = delegatedCodeAddress;
  }

  /**
   * Returns the delegated code.
   *
   * @return the delegated code.
   */
  @Override
  public Optional<Bytes> getDelegatedCode() {
    return resolveDelegatedCode();
  }

  /**
   * Returns the hash of the delegated code.
   *
   * @return the hash of the delegated code.
   */
  @Override
  public Optional<Hash> getDelegatedCodeHash() {
    return getDelegatedCode().map(Hash::hash);
  }

  /**
   * Returns the address of the delegated code.
   *
   * @return the address of the delegated code.
   */
  @Override
  public Optional<Address> delegatedCodeAddress() {
    return Optional.of(delegatedCodeAddress);
  }

  @Override
  public boolean hasDelegatedCode() {
    return true;
  }

  private Optional<Account> getDelegatedAccount() {
    return Optional.ofNullable(worldUpdater.getAccount(delegatedCodeAddress));
  }

  private Optional<Bytes> resolveDelegatedCode() {
    final Optional<Account> maybeDelegatedAccount = getDelegatedAccount();

    if (gasCalculator.isPrecompile(delegatedCodeAddress) || maybeDelegatedAccount.isEmpty()) {
      return Optional.of(Bytes.EMPTY);
    }

    return Optional.of(maybeDelegatedAccount.get().getCode());
  }
}
