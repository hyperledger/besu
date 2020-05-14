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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;

public class JsonRpcErrorConverter {

  public static JsonRpcError convertTransactionInvalidReason(
      final TransactionInvalidReason reason) {
    switch (reason) {
      case NONCE_TOO_LOW:
      case PRIVATE_NONCE_TOO_LOW:
        return JsonRpcError.NONCE_TOO_LOW;
      case INCORRECT_NONCE:
      case INCORRECT_PRIVATE_NONCE:
        return JsonRpcError.INCORRECT_NONCE;
      case INVALID_SIGNATURE:
        return JsonRpcError.INVALID_TRANSACTION_SIGNATURE;
      case INTRINSIC_GAS_EXCEEDS_GAS_LIMIT:
        return JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT;
      case UPFRONT_COST_EXCEEDS_BALANCE:
        return JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE;
      case EXCEEDS_BLOCK_GAS_LIMIT:
        return JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT;
      case WRONG_CHAIN_ID:
        return JsonRpcError.WRONG_CHAIN_ID;
      case REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED:
        return JsonRpcError.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED;
      case TX_SENDER_NOT_AUTHORIZED:
        return JsonRpcError.TX_SENDER_NOT_AUTHORIZED;
        // Private Transaction Invalid Reasons
      case CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE:
        return JsonRpcError.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;
      case GAS_PRICE_TOO_LOW:
        return JsonRpcError.GAS_PRICE_TOO_LOW;
      case OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST:
        return JsonRpcError.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST;
      case TRANSACTION_ALREADY_KNOWN:
        return JsonRpcError.ETH_SEND_TX_ALREADY_KNOWN;
      case TRANSACTION_REPLACEMENT_UNDERPRICED:
        return JsonRpcError.ETH_SEND_TX_REPLACEMENT_UNDERPRICED;
      default:
        return JsonRpcError.INVALID_PARAMS;
    }
  }
}
