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

/** Exception thrown when an operation is executed with insufficient gas. */
public class InsufficientGasException extends Exception {

  /** The gas cost that would be required to execute the operation. */
  private final long gasCost;

  /**
   * Creates a new {@link InsufficientGasException} with the given message.
   *
   * @param gasCost the gas cost
   * @param message the message
   */
  public InsufficientGasException(final long gasCost, final String message) {
    super(message);
    this.gasCost = gasCost;
  }

  /**
   * Returns the gas cost that would be required to execute the operation.
   *
   * @return the gas cost
   */
  public long getGasCost() {
    return gasCost;
  }
}
