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

import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

/**
 * An enumeration of errors that can occur during a block state call.
 *
 * <p>These errors are returned as JSON-RPC errors.
 */
public enum BlockStateCallError {
  /** Too many block calls. */
  TOO_MANY_BLOCK_CALLS(-38026, "Too many block calls"),
  /** The block number is not ascending. */
  BLOCK_NUMBERS_NOT_ASCENDING(-38020, "Block numbers are not ascending"),
  /** The timestamp is not ascending. */
  TIMESTAMPS_NOT_ASCENDING(-38021, "Timestamps are not ascending"),
  /** Invalid precompile address. */
  INVALID_PRECOMPILE_ADDRESS(-32000, "Invalid precompile address"),
  /** The nonce is invalid. */
  INVALID_NONCES(-32602, "Invalid nonces"),
  /** Upfront cost exceeds balance. */
  UPFRONT_COST_EXCEEDS_BALANCE(-38014, "Upfront cost exceeds balance"),
  /** Gas price too low. */
  GAS_PRICE_TOO_LOW(-32602, "Gas price too low"),
  /** Intrinsic gas exceeds gas limit. */
  INTRINSIC_GAS_EXCEEDS_GAS_LIMIT(-38013, "Intrinsic gas exceeds gas limit"),
  /** Unknown error. */
  UNKNOWN(-32602, "Internal error");

  private final int code;
  private final String message;

  /**
   * Create a new BlockStateCallError.
   *
   * @param code The error code.
   * @param message The error message.
   */
  BlockStateCallError(final int code, final String message) {
    this.code = code;
    this.message = message;
  }

  /**
   * Returns the error code.
   *
   * @return The error code.
   */
  public int getCode() {
    return code;
  }

  /**
   * Returns the error message.
   *
   * @param transactionInvalidReason The transaction invalid reason.
   * @return The error message.
   */
  public static BlockStateCallError of(final TransactionInvalidReason transactionInvalidReason) {
    return switch (transactionInvalidReason) {
      case UPFRONT_COST_EXCEEDS_BALANCE -> UPFRONT_COST_EXCEEDS_BALANCE;
      case GAS_PRICE_TOO_LOW -> GAS_PRICE_TOO_LOW;
      case INTRINSIC_GAS_EXCEEDS_GAS_LIMIT -> INTRINSIC_GAS_EXCEEDS_GAS_LIMIT;
      default -> UNKNOWN;
    };
  }

  /**
   * Returns the error message.
   *
   * @return The error message.
   */
  public String getMessage() {
    return message;
  }
}
