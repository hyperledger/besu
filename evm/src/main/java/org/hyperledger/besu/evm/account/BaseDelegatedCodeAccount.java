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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

class BaseDelegatedCodeAccount {
  private final WorldUpdater worldUpdater;

  /** The address of the account that has delegated code to be loaded into it. */
  protected final Address delegatedCodeAddress;

  private Bytes delegatedCode;
  private Hash codeHash;

  protected BaseDelegatedCodeAccount(
      final WorldUpdater worldUpdater, final Address delegatedCodeAddress) {
    this.worldUpdater = worldUpdater;
    this.delegatedCodeAddress = delegatedCodeAddress;
  }

  /**
   * Returns the delegated code.
   *
   * @return the delegated code.
   */
  protected Bytes getCode() {
    if (delegatedCode != null) {
      return delegatedCode;
    }

    delegatedCode = resolveDelegatedCode();

    return delegatedCode;
  }

  /**
   * Returns the hash of the delegated code.
   *
   * @return the hash of the delegated code.
   */
  protected Hash getCodeHash() {
    if (codeHash != null) {
      return codeHash;
    }

    codeHash = Hash.hash(getCode());

    return codeHash;
  }

  /**
   * Returns the address of the delegated code.
   *
   * @return the address of the delegated code.
   */
  protected Optional<Address> delegatedCodeAddress() {
    return Optional.of(delegatedCodeAddress);
  }

  private Bytes resolveDelegatedCode() {

    return Optional.ofNullable(worldUpdater.getAccount(delegatedCodeAddress))
        .map(Account::getUnprocessedCode)
        .orElse(Bytes.EMPTY);
  }
}
