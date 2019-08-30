/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea;

import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.ethereum.privacy.Restriction;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EeaSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final PrivateTransactionHandler privateTransactionHandler;
  private final TransactionPool transactionPool;
  private final JsonRpcParameter parameters;

  public EeaSendRawTransaction(
      final PrivateTransactionHandler privateTransactionHandler,
      final TransactionPool transactionPool,
      final JsonRpcParameter parameters) {
    this.privateTransactionHandler = privateTransactionHandler;
    this.transactionPool = transactionPool;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String rawPrivateTransaction = parameters.required(request.getParams(), 0, String.class);

    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = decodeRawTransaction(rawPrivateTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.DECODE_ERROR);
    }

    if (!privateTransaction.getValue().isZero()) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.VALUE_NOT_ZERO);
    }

    if (!privateTransaction.getRestriction().equals(Restriction.RESTRICTED)) {
      return new JsonRpcErrorResponse(
          request.getId(), JsonRpcError.UNIMPLEMENTED_PRIVATE_TRANSACTION_TYPE);
    }

    final String enclaveKey;
    try {
      enclaveKey = privateTransactionHandler.sendToOrion(privateTransaction);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(request.getId(), convertEnclaveInvalidReason(e.getMessage()));
    }

    final String privacyGroupId;
    try {
      privacyGroupId = privateTransactionHandler.getPrivacyGroup(enclaveKey, privateTransaction);

    } catch (final Exception e) {
      return new JsonRpcErrorResponse(request.getId(), convertEnclaveInvalidReason(e.getMessage()));
    }

    return privateTransactionHandler
        .validatePrivateTransaction(privateTransaction, privacyGroupId)
        .either(
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
                              request.getId(), convertTransactionInvalidReason(errorReason)));
            },
            (errorReason) ->
                new JsonRpcErrorResponse(
                    request.getId(), convertTransactionInvalidReason(errorReason)));
  }

  private PrivateTransaction decodeRawTransaction(final String hash)
      throws InvalidJsonRpcRequestException {
    try {
      return PrivateTransaction.readFrom(RLP.input(BytesValue.fromHexString(hash)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.debug(e);
      throw new InvalidJsonRpcRequestException("Invalid raw private transaction hex", e);
    }
  }
}
