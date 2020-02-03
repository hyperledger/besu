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

import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.utils.Base64String;
import org.web3j.utils.PrivacyGroupUtils;

public class PrivacySendTransaction {

  private static final Logger LOG = LogManager.getLogger();

  protected final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public PrivacySendTransaction(
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  public PrivateTransaction decode(final JsonRpcRequestContext request)
      throws ErrorResponseException {
    if (request.getRequest().getParamLength() != 1) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), INVALID_PARAMS));
    }
    final String rawPrivateTransaction = request.getRequiredParameter(0, String.class);
    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = decodeRawTransaction(rawPrivateTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), DECODE_ERROR));
    }
    return privateTransaction;
  }

  public void validate(
      final JsonRpcRequestContext request, final PrivateTransaction privateTransaction)
      throws ErrorResponseException {
    final String privacyGroupId;
    try {
      privacyGroupId = privacyGroupId(privateTransaction);
    } catch (IllegalArgumentException e) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(request.getRequest().getId(), INVALID_PARAMS));
    }

    final String enclaveKey = enclavePublicKeyProvider.getEnclaveKey(request.getUser());
    final ValidationResult<TransactionInvalidReason> transactionValidationResult =
        privacyController.validatePrivateTransaction(
            privateTransaction, privacyGroupId, enclaveKey);
    if (!transactionValidationResult.isValid()) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(
              request.getRequest().getId(),
              convertTransactionInvalidReason(transactionValidationResult.getInvalidReason())));
    }
  }

  public String sendToEnclave(
      final PrivateTransaction privateTransaction, final JsonRpcRequestContext requestContext)
      throws ErrorResponseException {
    try {
      return privacyController.sendTransaction(
          privateTransaction, enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));
    } catch (final Exception e) {
      throw new ErrorResponseException(
          new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), convertEnclaveInvalidReason(e.getMessage())));
    }
  }

  private PrivateTransaction decodeRawTransaction(final String hash)
      throws InvalidJsonRpcRequestException {
    try {
      return PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(hash)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.debug(e);
      throw new InvalidJsonRpcRequestException("Invalid raw private transaction hex", e);
    }
  }

  private String privacyGroupId(final PrivateTransaction privateTransaction) {
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return privateTransaction.getPrivacyGroupId().get().toBase64String();
    } else {
      final Base64String privateFrom =
          Base64String.wrap(privateTransaction.getPrivateFrom().toBase64String());
      final List<Base64String> privateFor =
          privateTransaction
              .getPrivateFor()
              .map(
                  bytes ->
                      bytes.stream()
                          .map(Bytes::toBase64String)
                          .map(Base64String::wrap)
                          .collect(Collectors.toList()))
              .orElse(Lists.newArrayList());
      return PrivacyGroupUtils.generateLegacyGroup(privateFrom, privateFor).toString();
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
