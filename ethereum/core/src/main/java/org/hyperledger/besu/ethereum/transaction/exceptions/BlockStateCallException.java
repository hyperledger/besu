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
package org.hyperledger.besu.ethereum.transaction.exceptions;

import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.Optional;

/** Exception thrown when a block state call fails. */
public class BlockStateCallException extends RuntimeException {

  /** The result of the block state call. */
  private final TransactionSimulatorResult result;

  /**
   * Constructs a new BlockStateCallException with the given message.
   *
   * @param message the message
   */
  public BlockStateCallException(final String message) {
    super(message);
    this.result = null;
  }

  /**
   * Constructs a new BlockStateCallException with the given message and result.
   *
   * @param message the message
   * @param result the result
   */
  public BlockStateCallException(final String message, final TransactionSimulatorResult result) {
    super(message);
    this.result = result;
  }

  /**
   * Gets the result of the block state call.
   *
   * @return the result
   */
  public Optional<TransactionSimulatorResult> getResult() {
    return Optional.ofNullable(result);
  }
}
