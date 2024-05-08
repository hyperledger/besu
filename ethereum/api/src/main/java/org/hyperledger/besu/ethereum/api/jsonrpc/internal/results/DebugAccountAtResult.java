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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.immutables.value.Value;

/** The type Debug account at result. */
@Value.Immutable
public abstract class DebugAccountAtResult implements JsonRpcResult {
  /** Default Constructor */
  public DebugAccountAtResult() {}

  /**
   * Gets code.
   *
   * @return the code
   */
  public abstract String getCode();

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  public abstract String getNonce();

  /**
   * Gets balance.
   *
   * @return the balance
   */
  public abstract String getBalance();

  /**
   * Gets codehash.
   *
   * @return the codehash
   */
  public abstract String getCodehash();
}
