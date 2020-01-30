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
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacySendTransaction.ErrorResponseException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import org.apache.logging.log4j.Logger;

public class EeaSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final PrivacySendTransaction privacySendTransaction;
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
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final PrivateTransaction privateTransaction = privacySendTransaction.decode(requestContext);
      privacySendTransaction.validate(requestContext, privateTransaction);
      final Transaction privacyMarkerTransaction =
          createMarkerTransaction(requestContext, privateTransaction);
      final Object id = requestContext.getRequest().getId();
      return transactionPool
          .addLocalTransaction(privacyMarkerTransaction)
          .either(
              () -> new JsonRpcSuccessResponse(id, privacyMarkerTransaction.getHash().toString()),
              errorReason ->
                  new JsonRpcErrorResponse(id, convertTransactionInvalidReason(errorReason)));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), ENCLAVE_ERROR);
    } catch (final ErrorResponseException e) {
      return e.getResponse();
    }
  }

  private Transaction createMarkerTransaction(
      final JsonRpcRequestContext requestContext, final PrivateTransaction privateTransaction)
      throws ErrorResponseException {
    final String enclaveKey =
        privacySendTransaction.sendToEnclave(privateTransaction, requestContext);
    return privacyController.createPrivacyMarkerTransaction(enclaveKey, privateTransaction);
  }
}
