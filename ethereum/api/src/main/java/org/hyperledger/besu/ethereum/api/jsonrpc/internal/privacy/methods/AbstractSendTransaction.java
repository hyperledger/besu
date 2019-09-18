/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AbstractSendTransaction {

  private static final Logger LOG = LogManager.getLogger();

  protected final PrivateTransactionHandler privateTransactionHandler;
  private final JsonRpcParameter parameters;
  protected final TransactionPool transactionPool;

  public AbstractSendTransaction(
      final PrivateTransactionHandler privateTransactionHandler,
      final TransactionPool transactionPool,
      final JsonRpcParameter parameters) {
    this.privateTransactionHandler = privateTransactionHandler;
    this.parameters = parameters;
    this.transactionPool = transactionPool;
  }

  protected PrivateTransaction validateRequest(final JsonRpcRequest request)
      throws ErrorResponseException {
    if (request.getParamLength() != 1) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS));
    }
    final String rawPrivateTransaction = parameters.required(request.getParams(), 0, String.class);
    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = decodeRawTransaction(rawPrivateTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getId(), JsonRpcError.DECODE_ERROR));
    }
    if (!privateTransaction.getValue().isZero()) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getId(), JsonRpcError.VALUE_NOT_ZERO));
    }
    if (!privateTransaction.getRestriction().equals(Restriction.RESTRICTED)) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(
              request.getId(), JsonRpcError.UNIMPLEMENTED_PRIVATE_TRANSACTION_TYPE));
    }
    return privateTransaction;
  }

  protected JsonRpcResponse validateAndExecute(
      final JsonRpcRequest request,
      final PrivateTransaction privateTransaction,
      final String privacyGroupId,
      AfterTransactionValid afterValid) {
    return privateTransactionHandler
        .validatePrivateTransaction(privateTransaction, privacyGroupId)
        .either(
            afterValid::getResponse,
            (errorReason) ->
                new JsonRpcErrorResponse(
                    request.getId(),
                    JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
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

  protected static class ErrorResponseException extends Exception {
    private final JsonRpcResponse response;

    private ErrorResponseException(final JsonRpcResponse response) {
      super();
      this.response = response;
    }

    public JsonRpcResponse getResponse() {
      return response;
    }
  }

  protected interface AfterTransactionValid {
    JsonRpcResponse getResponse();
  }
}
