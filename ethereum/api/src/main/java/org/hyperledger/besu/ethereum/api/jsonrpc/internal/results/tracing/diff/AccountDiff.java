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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff;

import java.util.Map;

/** The type Account diff. */
public final class AccountDiff {

  private final DiffNode balance;
  private final DiffNode code;
  private final DiffNode nonce;
  private final Map<String, DiffNode> storage;

  /**
   * Instantiates a new Account diff.
   *
   * @param balance the balance
   * @param code the code
   * @param nonce the nonce
   * @param storage the storage
   */
  AccountDiff(
      final DiffNode balance,
      final DiffNode code,
      final DiffNode nonce,
      final Map<String, DiffNode> storage) {
    this.balance = balance;
    this.code = code;
    this.nonce = nonce;
    this.storage = storage;
  }

  /**
   * Gets balance.
   *
   * @return the balance
   */
  public DiffNode getBalance() {
    return balance;
  }

  /**
   * Gets code.
   *
   * @return the code
   */
  public DiffNode getCode() {
    return code;
  }

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  public DiffNode getNonce() {
    return nonce;
  }

  /**
   * Gets storage.
   *
   * @return the storage
   */
  public Map<String, DiffNode> getStorage() {
    return storage;
  }

  /**
   * Has difference boolean.
   *
   * @return the boolean
   */
  boolean hasDifference() {
    return balance.hasDifference()
        || code.hasDifference()
        || nonce.hasDifference()
        || !storage.isEmpty();
  }
}
