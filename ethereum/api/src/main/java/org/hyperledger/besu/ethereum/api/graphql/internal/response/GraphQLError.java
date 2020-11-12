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
package org.hyperledger.besu.ethereum.api.graphql.internal.response;

import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum GraphQLError {
  // Standard errors
  INVALID_PARAMS(-32602, "Invalid params"),
  INTERNAL_ERROR(-32603, "Internal error"),

  // Transaction validation failures
  NONCE_TOO_LOW(-32001, "Nonce too low"),
  INVALID_TRANSACTION_SIGNATURE(-32002, "Invalid signature"),
  INTRINSIC_GAS_EXCEEDS_LIMIT(-32003, "Intrinsic gas exceeds gas limit"),
  TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE(-32004, "Upfront cost exceeds account balance"),
  EXCEEDS_BLOCK_GAS_LIMIT(-32005, "Transaction gas limit exceeds block gas limit"),
  INCORRECT_NONCE(-32006, "Incorrect nonce"),
  TX_SENDER_NOT_AUTHORIZED(-32007, "Sender account not authorized to send transactions"),
  CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE(-32008, "Initial sync is still in progress"),
  GAS_PRICE_TOO_LOW(-32009, "Gas price below configured minimum gas price"),
  WRONG_CHAIN_ID(-32000, "Wrong Chain ID in transaction signature"),
  REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED(
      -32000, "Signatures with replay protection are not supported"),
  TX_FEECAP_EXCEEDED(-32000, "Transaction fee cap exceeded"),

  // Private Transaction Errors
  PRIVATE_TRANSACTION_FAILED(-32000, "Private transaction failed"),
  PRIVATE_NONCE_TOO_LOW(-50100, "Private transaction nonce too low"),
  INCORRECT_PRIVATE_NONCE(-50100, "Private transaction nonce is incorrect");

  private final int code;
  private final String message;

  GraphQLError(final int code, final String message) {
    this.code = code;
    this.message = message;
  }

  @JsonGetter("code")
  public int getCode() {
    return code;
  }

  @JsonGetter("message")
  public String getMessage() {
    return message;
  }

  public static GraphQLError of(final TransactionInvalidReason transactionInvalidReason) {
    switch (transactionInvalidReason) {
      case WRONG_CHAIN_ID:
        return WRONG_CHAIN_ID;
      case REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED:
        return REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
      case INVALID_SIGNATURE:
        return INVALID_TRANSACTION_SIGNATURE;
      case UPFRONT_COST_EXCEEDS_BALANCE:
        return TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE;
      case NONCE_TOO_LOW:
      case PRIVATE_NONCE_TOO_LOW:
        return NONCE_TOO_LOW;
      case INCORRECT_NONCE:
      case INCORRECT_PRIVATE_NONCE:
        return INCORRECT_NONCE;
      case INTRINSIC_GAS_EXCEEDS_GAS_LIMIT:
        return INTRINSIC_GAS_EXCEEDS_LIMIT;
      case EXCEEDS_BLOCK_GAS_LIMIT:
        return EXCEEDS_BLOCK_GAS_LIMIT;
      case TX_SENDER_NOT_AUTHORIZED:
        return TX_SENDER_NOT_AUTHORIZED;
      case CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE:
        return CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;
        // Private Transaction Invalid Reasons
      case PRIVATE_TRANSACTION_FAILED:
        return PRIVATE_TRANSACTION_FAILED;
      case GAS_PRICE_TOO_LOW:
        return GAS_PRICE_TOO_LOW;
      case TX_FEECAP_EXCEEDED:
        return TX_FEECAP_EXCEEDED;
      default:
        return INTERNAL_ERROR;
    }
  }
}
