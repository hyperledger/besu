/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.core;

/**
 * A world state account.
 *
 * <p>In addition to holding the account state, a full account provides access to the account
 * address, which is not stored directly in the world state trie (account's are indexed by the hash
 * of their address).
 */
public interface Account extends AccountState {

  long DEFAULT_NONCE = 0L;
  Wei DEFAULT_BALANCE = Wei.ZERO;
  int DEFAULT_VERSION = 0;

  /**
   * The account address.
   *
   * @return the account address
   */
  Address getAddress();
}
