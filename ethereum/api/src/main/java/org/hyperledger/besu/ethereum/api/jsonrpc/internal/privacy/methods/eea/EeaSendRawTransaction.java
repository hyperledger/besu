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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.AbstractSendTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;

public class EeaSendRawTransaction extends AbstractSendTransaction implements JsonRpcMethod {

  public EeaSendRawTransaction(
      final PrivateTransactionHandler privateTransactionHandler,
      final TransactionPool transactionPool,
      final JsonRpcParameter parameters) {
    super(privateTransactionHandler, transactionPool, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    PrivateTransaction privateTransaction;
    try {
      privateTransaction = validateAndDecodeRequest(request);
    } catch (ErrorResponseException e) {
      return e.getResponse();
    }

    final String enclaveKey;
    try {
      enclaveKey = privateTransactionHandler.sendToOrion(privateTransaction);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          request.getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }

    final String privacyGroupId;
    try {
      privacyGroupId = privateTransactionHandler.getPrivacyGroup(enclaveKey, privateTransaction);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          request.getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }

    return validateAndExecute(
        request,
        privateTransaction,
        privacyGroupId,
        () -> {
          final Transaction privacyMarkerTransaction =
              privateTransactionHandler.createPrivacyMarkerTransaction(
                  enclaveKey, privateTransaction);
          return transactionPool
              .addLocalTransaction(privacyMarkerTransaction)
              .either(
                  () ->
                      new JsonRpcSuccessResponse(
                          request.getId(), privacyMarkerTransaction.hash().toString()),
                  errorReason ->
                      new JsonRpcErrorResponse(
                          request.getId(),
                          JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
        });
  }
}
