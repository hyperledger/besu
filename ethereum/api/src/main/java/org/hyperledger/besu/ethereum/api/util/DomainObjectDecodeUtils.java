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
package org.hyperledger.besu.ethereum.api.util;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import org.apache.tuweni.bytes.Bytes;

public class DomainObjectDecodeUtils {

  public static Transaction decodeRawTransaction(final String rawTransaction)
      throws InvalidJsonRpcRequestException {
    try {
      Bytes txnBytes = Bytes.fromHexString(rawTransaction);
      return TransactionDecoder.decodeOpaqueBytes(txnBytes, EncodingContext.POOLED_TRANSACTION);
    } catch (final IllegalArgumentException e) {
      throw new InvalidJsonRpcRequestException(
          "Invalid raw transaction hex", RpcErrorType.INVALID_TRANSACTION_PARAMS, e);
    } catch (final RLPException r) {
      throw new InvalidJsonRpcRequestException(
          "Invalid RLP in raw transaction hex", RpcErrorType.INVALID_TRANSACTION_PARAMS, r);
    }
  }
}
