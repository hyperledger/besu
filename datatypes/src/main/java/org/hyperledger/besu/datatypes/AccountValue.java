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
 */
package org.hyperledger.besu.datatypes;

import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public interface AccountValue {
  long getNonce();

  /**
   * The available balance of that account.
   *
   * @return the balance, in Wei, of the account.
   */
  Wei getBalance();

  /**
   * The hash of the root of the storage trie associated with this account.
   *
   * @return the hash of the root node of the storage trie.
   */
  Hash getStorageRoot();

  /**
   * The hash of the EVM bytecode associated with this account.
   *
   * @return the hash of the account code (which may be {@link Hash#EMPTY}).
   */
  Hash getCodeHash();

  /** Writes the account value to the provided {@link RLPOutput}. */
  void writeTo(final RLPOutput out);
}
