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

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.UNAUTHORIZED;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction.ErrorResponseException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.SendTransactionResponse;

import org.apache.logging.log4j.Logger;

public class EeaSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final PrivacySendTransaction privacySendTransaction;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  private final TransactionPool transactionPool;
  private final PrivacyController privacyController;

  public EeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.transactionPool = transactionPool;
    this.privacyController = privacyController;
    this.privacySendTransaction =
        new PrivacySendTransaction(privacyController, enclavePublicKeyProvider);
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final PrivateTransaction privateTransaction;
    try {
      privateTransaction = privacySendTransaction.validateAndDecodeRequest(requestContext);
    } catch (final ErrorResponseException e) {
      return e.getResponse();
    }

    final SendTransactionResponse sendTransactionResponse;
    try {
      sendTransactionResponse =
          privacyController.sendTransaction(
              privateTransaction, enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcUnauthorizedResponse(requestContext.getRequest().getId(), UNAUTHORIZED);
    } catch (final EnclaveClientException | EnclaveServerException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), convertEnclaveInvalidReason(e.getMessage()));
    }

    return privacySendTransaction.validateAndExecute(
        requestContext,
        privateTransaction,
        sendTransactionResponse.getPrivacyGroupId(),
        () -> {
          final Transaction privacyMarkerTransaction =
              privacyController.createPrivacyMarkerTransaction(
                  sendTransactionResponse.getEnclaveKey(), privateTransaction);
          return transactionPool
              .addLocalTransaction(privacyMarkerTransaction)
              .either(
                  () ->
                      new JsonRpcSuccessResponse(
                          requestContext.getRequest().getId(),
                          privacyMarkerTransaction.getHash().toString()),
                  errorReason ->
                      new JsonRpcErrorResponse(
                          requestContext.getRequest().getId(),
                          JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
        });
  }
}
