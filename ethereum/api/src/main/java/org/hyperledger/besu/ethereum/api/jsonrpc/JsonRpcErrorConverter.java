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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

public class JsonRpcErrorConverter {

  public static RpcErrorType convertTransactionInvalidReason(
      final TransactionInvalidReason reason) {
    switch (reason) {
      case NONCE_TOO_LOW:
      case PRIVATE_NONCE_TOO_LOW:
        return RpcErrorType.NONCE_TOO_LOW;
      case NONCE_TOO_HIGH:
      case PRIVATE_NONCE_TOO_HIGH:
        return RpcErrorType.NONCE_TOO_HIGH;
      case INVALID_SIGNATURE:
        return RpcErrorType.INVALID_TRANSACTION_SIGNATURE;
      case INTRINSIC_GAS_EXCEEDS_GAS_LIMIT:
        return RpcErrorType.INTRINSIC_GAS_EXCEEDS_LIMIT;
      case UPFRONT_COST_EXCEEDS_BALANCE:
        return RpcErrorType.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE;
      case EXCEEDS_BLOCK_GAS_LIMIT:
        return RpcErrorType.EXCEEDS_BLOCK_GAS_LIMIT;
      case WRONG_CHAIN_ID:
        return RpcErrorType.WRONG_CHAIN_ID;
      case REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED:
        return RpcErrorType.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
      case REPLAY_PROTECTED_SIGNATURE_REQUIRED:
        return RpcErrorType.REPLAY_PROTECTED_SIGNATURE_REQUIRED;
      case TX_SENDER_NOT_AUTHORIZED:
        return RpcErrorType.TX_SENDER_NOT_AUTHORIZED;
        // Private Transaction Invalid Reasons
      case PRIVATE_TRANSACTION_INVALID:
        return RpcErrorType.PRIVATE_TRANSACTION_INVALID;
      case PRIVATE_TRANSACTION_FAILED:
        return RpcErrorType.PRIVATE_TRANSACTION_FAILED;
      case PRIVATE_UNIMPLEMENTED_TRANSACTION_TYPE:
        return RpcErrorType.UNSUPPORTED_PRIVATE_TRANSACTION_TYPE;
      case CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE:
        return RpcErrorType.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;
      case GAS_PRICE_TOO_LOW:
        return RpcErrorType.GAS_PRICE_TOO_LOW;
      case GAS_PRICE_BELOW_CURRENT_BASE_FEE:
        return RpcErrorType.GAS_PRICE_BELOW_CURRENT_BASE_FEE;
      case TX_FEECAP_EXCEEDED:
        return RpcErrorType.TX_FEECAP_EXCEEDED;
      case MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS:
        return RpcErrorType.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS;
      case OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST:
        return RpcErrorType.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST;
      case INVALID_TRANSACTION_FORMAT:
        return RpcErrorType.INVALID_TRANSACTION_TYPE;
      case TRANSACTION_ALREADY_KNOWN:
        return RpcErrorType.ETH_SEND_TX_ALREADY_KNOWN;
      case TRANSACTION_REPLACEMENT_UNDERPRICED:
        return RpcErrorType.ETH_SEND_TX_REPLACEMENT_UNDERPRICED;
      case NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER:
        return RpcErrorType.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
      case TOTAL_BLOB_GAS_TOO_HIGH:
        return RpcErrorType.TOTAL_BLOB_GAS_TOO_HIGH;
      case TX_POOL_DISABLED:
        return RpcErrorType.TX_POOL_DISABLED;
      case PLUGIN_TX_POOL_VALIDATOR:
        return RpcErrorType.PLUGIN_TX_VALIDATOR;
      case INVALID_BLOBS:
        return RpcErrorType.INVALID_BLOBS;
      case BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE:
        return RpcErrorType.BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE;
      case EXECUTION_HALTED:
        return RpcErrorType.EXECUTION_HALTED;
      case BLOCK_NOT_FOUND:
        return RpcErrorType.BLOCK_NOT_FOUND;
      default:
        return RpcErrorType.INTERNAL_ERROR;
    }
  }
}
