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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.enclavePublicKey;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacySendTransaction {

  private static final Logger LOG = LogManager.getLogger();

  protected final PrivacyController privacyController;

  public PrivacySendTransaction(final PrivacyController privacyController) {
    this.privacyController = privacyController;
  }

  public PrivateTransaction validateAndDecodeRequest(final JsonRpcRequestContext request)
      throws ErrorResponseException {
    if (request.getRequest().getParamLength() != 1) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS));
    }
    final String rawPrivateTransaction = request.getRequiredParameter(0, String.class);
    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = decodeRawTransaction(rawPrivateTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR));
    }
    if (!privateTransaction.getValue().isZero()) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.VALUE_NOT_ZERO));
    }
    if (!privateTransaction.getRestriction().equals(Restriction.RESTRICTED)) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(
              request.getRequest().getId(), JsonRpcError.UNIMPLEMENTED_PRIVATE_TRANSACTION_TYPE));
    }
    return privateTransaction;
  }

  public JsonRpcResponse validateAndExecute(
      final JsonRpcRequestContext request,
      final PrivateTransaction privateTransaction,
      final String privacyGroupId,
      final Supplier<JsonRpcResponse> successfulJsonRpcResponse) {
    return privacyController
        .validatePrivateTransaction(
            privateTransaction, privacyGroupId, enclavePublicKey(request.getUser()))
        .either(
            successfulJsonRpcResponse,
            (errorReason) ->
                new JsonRpcErrorResponse(
                    request.getRequest().getId(),
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

  public static class ErrorResponseException extends Exception {
    private final JsonRpcResponse response;

    private ErrorResponseException(final JsonRpcResponse response) {
      super();
      this.response = response;
    }

    public JsonRpcResponse getResponse() {
      return response;
    }
  }
}
