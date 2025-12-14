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

/** Exception thrown when a block state call validation fails. */
public class BlockStateCallValidationException extends RuntimeException {

  /** The block state call error. */
  private final BlockStateCallError error;

  /**
   * Constructs an exception with a message and an error descriptor.
   *
   * @param message the message
   * @param error the block state call error
   */
  public BlockStateCallValidationException(final String message, final BlockStateCallError error) {
    super(message);
    this.error = error;
  }

  /**
   * Returns the block state call error, if present.
   *
   * @return optional error descriptor
   */
  public BlockStateCallError getError() {
    return error;
  }
}
