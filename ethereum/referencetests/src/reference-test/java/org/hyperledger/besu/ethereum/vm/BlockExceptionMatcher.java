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
package org.hyperledger.besu.ethereum.vm;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.base.Splitter;

/**
 * Maps execution-spec-tests exception names (e.g., BlockException.GAS_USED_OVERFLOW) to predicates
 * that match Besu's actual error messages. This ensures reference tests fail for the expected
 * reason, not an unrelated one.
 *
 * <p>The expectException field in test fixtures uses "|" to separate alternative acceptable
 * exceptions. The block is considered correctly rejected if the actual error matches ANY of the
 * alternatives.
 */
final class BlockExceptionMatcher {

  private BlockExceptionMatcher() {}

  private static final Map<String, Predicate<String>> MATCHERS =
      Map.ofEntries(
          // Block-level exceptions
          entry("BlockException.GAS_USED_OVERFLOW", "provided gas insufficient", "gas used mismatch"),
          entry(
              "BlockException.INCORRECT_BLOB_GAS_USED",
              "blob gas",
              "did not consume expected blob gas",
              "header validation failed"),
          entry(
              "BlockException.INCORRECT_EXCESS_BLOB_GAS",
              "excess blob gas",
              "excessblobgas",
              "header validation failed"),
          entry(
              "BlockException.INCORRECT_BLOCK_FORMAT",
              "header validation failed",
              "rlp",
              "block access list validation failed"),
          entry("BlockException.INVALID_BASEFEE_PER_GAS", "basefee", "base fee"),
          entry("BlockException.INVALID_GASLIMIT", "gas limit", "GasLimit"),
          entry("BlockException.INVALID_REQUESTS", "equests hash mismatch", "equests"),
          entry(
              "BlockException.INVALID_DEPOSIT_EVENT_LAYOUT",
              "eposit",
              "equests",
              "invalid amount",
              "invalid pubkey",
              "invalid signature",
              "invalid index",
              "invalid withdrawal"),
          entry("BlockException.SYSTEM_CONTRACT_CALL_FAILED", "system", "contract"),
          entry("BlockException.SYSTEM_CONTRACT_EMPTY", "system", "empty"),
          entry("BlockException.RLP_BLOCK_LIMIT_EXCEEDED", "Block size", "exceeds limit"),
          entry("BlockException.RLP_STRUCTURES_ENCODING", "RLP", "decode", "encoding"),
          entry("BlockException.INVALID_VERSIONED_HASHES", "versioned hash", "blob"),
          entry(
              "BlockException.BLOB_GAS_USED_ABOVE_LIMIT",
              "blob gas",
              "did not consume expected blob gas",
              "header validation failed"),
          entry(
              "BlockException.INVALID_BLOCK_HASH",
              "Hash",
              "hash"),
          entry(
              "BlockException.INVALID_WITHDRAWALS_ROOT",
              "ithdrawal",
              "root"),
          // BAL-related block exceptions
          entry(
              "BlockException.INVALID_BLOCK_ACCESS_LIST",
              "lock access list"),
          entry(
              "BlockException.BLOCK_ACCESS_LIST_GAS_LIMIT_EXCEEDED",
              "lock access list"),
          entry(
              "BlockException.INVALID_BAL_EXTRA_ACCOUNT",
              "lock access list"),
          entry(
              "BlockException.INVALID_BAL_MISSING_ACCOUNT",
              "lock access list"),
          // Transaction-level exceptions (block rejected due to invalid tx)
          entry(
              "TransactionException.INTRINSIC_GAS_TOO_LOW",
              "intrinsic gas",
              "ntrinsic gas too low"),
          entry(
              "TransactionException.INTRINSIC_GAS_BELOW_FLOOR_GAS_COST",
              "intrinsic gas",
              "floor"),
          entry(
              "TransactionException.INSUFFICIENT_ACCOUNT_FUNDS",
              "insufficient funds",
              "upfront cost",
              "balance"),
          entry(
              "TransactionException.NONCE_MISMATCH_TOO_LOW",
              "nonce",
              "too low"),
          entry(
              "TransactionException.NONCE_MISMATCH_TOO_HIGH",
              "nonce",
              "too high"),
          entry("TransactionException.NONCE_IS_MAX", "nonce", "max"),
          entry(
              "TransactionException.GAS_ALLOWANCE_EXCEEDED",
              "gas limit",
              "exceeds",
              "provided gas insufficient"),
          entry(
              "TransactionException.GAS_LIMIT_EXCEEDS_MAXIMUM",
              "gas limit exceeds",
              "gas limit must be at most"),
          entry(
              "TransactionException.INSUFFICIENT_MAX_FEE_PER_GAS",
              "fee per gas",
              "max fee",
              "gasprice is less than",
              "basefee"),
          entry(
              "TransactionException.INSUFFICIENT_MAX_FEE_PER_BLOB_GAS",
              "blob gas price",
              "fee per blob"),
          entry(
              "TransactionException.PRIORITY_GREATER_THAN_MAX_FEE_PER_GAS",
              "priority",
              "max fee"),
          entry("TransactionException.SENDER_NOT_EOA", "sender", "EOA", "not externally owned"),
          entry("TransactionException.INITCODE_SIZE_EXCEEDED", "initcode", "size"),
          entry(
              "TransactionException.GASLIMIT_PRICE_PRODUCT_OVERFLOW",
              "overflow",
              "upfront gas cost",
              "cannot exceed"),
          // Type 3 (blob) transaction exceptions
          entry(
              "TransactionException.TYPE_3_TX_PRE_FORK",
              "blob",
              "transaction type"),
          entry(
              "TransactionException.TYPE_3_TX_CONTRACT_CREATION",
              "blob",
              "contract creation"),
          entry(
              "TransactionException.TYPE_3_TX_ZERO_BLOBS",
              "blob",
              "zero"),
          entry(
              "TransactionException.TYPE_3_TX_BLOB_COUNT_EXCEEDED",
              "blob",
              "count",
              "exceeds",
              "header validation failed"),
          entry(
              "TransactionException.TYPE_3_TX_INVALID_BLOB_VERSIONED_HASH",
              "blob",
              "versioned hash"),
          entry(
              "TransactionException.TYPE_3_TX_MAX_BLOB_GAS_ALLOWANCE_EXCEEDED",
              "blob gas",
              "exceeds",
              "header validation failed"),
          entry(
              "TransactionException.TYPE_3_TX_WITH_FULL_BLOBS",
              "blob",
              "RLP"),
          // Type 1 / Type 2 pre-fork
          entry("TransactionException.TYPE_1_TX_PRE_FORK", "transaction type"),
          entry("TransactionException.TYPE_2_TX_PRE_FORK", "transaction type"),
          // Type 4 (set code) transaction exceptions
          entry(
              "TransactionException.TYPE_4_TX_PRE_FORK",
              "transaction type",
              "set code"),
          entry(
              "TransactionException.TYPE_4_EMPTY_AUTHORIZATION_LIST",
              "authorization",
              "empty"),
          entry(
              "TransactionException.TYPE_4_TX_CONTRACT_CREATION",
              "set code",
              "contract creation",
              "code delegation",
              "must have a to address"));

  /**
   * Returns true if the actual error message from Besu matches the expected exception(s). The
   * expectedExceptions string may contain "|"-separated alternatives.
   */
  static boolean matches(final String expectedExceptions, final String actualError) {
    final String lowerError = actualError.toLowerCase(Locale.ROOT);

    for (final String exception : Splitter.on('|').split(expectedExceptions)) {
      final String trimmed = exception.trim();
      final Predicate<String> matcher = MATCHERS.get(trimmed);
      if (matcher != null && matcher.test(lowerError)) {
        return true;
      }
    }
    return false;
  }

  private static Map.Entry<String, Predicate<String>> entry(
      final String exceptionName, final String... keywords) {
    return Map.entry(
        exceptionName,
        error -> {
          for (final String keyword : keywords) {
            if (error.contains(keyword.toLowerCase(Locale.ROOT))) {
              return true;
            }
          }
          return false;
        });
  }
}
