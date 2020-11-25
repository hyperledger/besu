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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonRpcErrorConverterTest {

  @Parameters
  public static Collection<Object[]> expectedErrorMapping() {
    return Arrays.asList(
        new Object[][] {
          {TransactionInvalidReason.NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW},
          {TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW},
          {TransactionInvalidReason.INCORRECT_NONCE, JsonRpcError.INCORRECT_NONCE},
          {TransactionInvalidReason.INCORRECT_PRIVATE_NONCE, JsonRpcError.INCORRECT_NONCE},
          {TransactionInvalidReason.INVALID_SIGNATURE, JsonRpcError.INVALID_TRANSACTION_SIGNATURE},
          {
            TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
            JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT
          },
          {
            TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
            JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE
          },
          {TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT},
          {TransactionInvalidReason.WRONG_CHAIN_ID, JsonRpcError.WRONG_CHAIN_ID},
          {
            TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
            JsonRpcError.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED
          },
          {
            TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, JsonRpcError.TX_SENDER_NOT_AUTHORIZED
          },
          {
            TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE,
            JsonRpcError.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE
          },
          {TransactionInvalidReason.GAS_PRICE_TOO_LOW, JsonRpcError.GAS_PRICE_TOO_LOW},
          {
            TransactionInvalidReason.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST,
            JsonRpcError.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST
          },
          {
            TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN,
            JsonRpcError.ETH_SEND_TX_ALREADY_KNOWN
          },
          {
            TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED,
            JsonRpcError.ETH_SEND_TX_REPLACEMENT_UNDERPRICED
          },
          {TransactionInvalidReason.GAS_PRICE_MUST_BE_ZERO, JsonRpcError.GAS_PRICE_MUST_BE_ZERO}
        });
  }

  @Parameter(0)
  public TransactionInvalidReason txInvalidReason;

  @Parameter(1)
  public JsonRpcError expectedJsonRpcError;

  @Test
  public void expectedTransactionValidationToJsonRpcErrorConversion() {
    assertThat(JsonRpcErrorConverter.convertTransactionInvalidReason(txInvalidReason))
        .isEqualTo(expectedJsonRpcError);
  }
}
