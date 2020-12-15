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

import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.QuorumSendRawTxArgs;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.net.URI;
import java.net.URISyntaxException;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;

public class QuorumSendRawPrivateTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  final TransactionPool transactionPool;
  final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public QuorumSendRawPrivateTransaction(
      final TransactionPool transactionPool,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.transactionPool = transactionPool;
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);

    final QuorumSendRawTxArgs rawTxArgs = requestContext.getRequiredParameter(1, QuorumSendRawTxArgs.class);


    try {
      final Transaction transaction =
          Transaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

    if (!checkAndHandlePrivateTransaction(transaction, rawTxArgs, requestContext)) {
        return new JsonRpcErrorResponse(id, INVALID_PARAMS);
      }


//      final ValidationResult<TransactionInvalidReason> validationResult =
//          privacyController.validatePrivateTransaction(transaction, enclavePublicKey);
//
//      if (!validationResult.isValid()) {
//        return new JsonRpcErrorResponse(
//            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
//      }

      // In quorum they validate if the account can transact from the current node before adding the Tx to the pool (Permissioning)

      // In quorum they are getting the Sender from the Tx AFTER adding the Tx to the pool. If that fails they are
      // returning an error

      return transactionPool
          .addLocalTransaction(transaction)
          .either(
              () -> new JsonRpcSuccessResponse(id, transaction.getHash().toString()),
              errorReason -> getJsonRpcErrorResponse(id, errorReason));

    } catch (final JsonRpcErrorResponseException e) {
      return new JsonRpcErrorResponse(id, e.getJsonRpcError());
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error(e);
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      LOG.error(e);
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  private boolean checkAndHandlePrivateTransaction(final Transaction transaction, final QuorumSendRawTxArgs rawTxArgs, final JsonRpcRequestContext requestContext) throws URISyntaxException {
    // rawTxArgs cannot be null as the call to getRequiredParameter would have failed if it was not available

    if (rawTxArgs.getPrivateFor() == null) {
      LOG.error(JsonRpcError.QUORUM_NO_PRIVATE_FOR.getMessage());
      throw new JsonRpcErrorResponseException(JsonRpcError.QUORUM_NO_PRIVATE_FOR);
    }

    if (rawTxArgs.getPrivacyFlag() != 0) {
      LOG.error(JsonRpcError.QUORUM_ONLY_STANDARD_MODE_SUPPORTED.getMessage());
      throw new JsonRpcErrorResponseException(JsonRpcError.QUORUM_ONLY_STANDARD_MODE_SUPPORTED);
    }

    if (rawTxArgs.getPrivateFrom() != null) {
      final String privateFrom = rawTxArgs.getPrivateFrom();
      final String enclavePublicKey =
            enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());
      if (!privateFrom.equals(enclavePublicKey)) {
        LOG.error(JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY.getMessage());
        throw new JsonRpcErrorResponseException(JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
      }
    }

    final EnclaveFactory enclaveFactory = new EnclaveFactory(Vertx.vertx());
    final GoQuorumEnclave goQuorumEnclave = enclaveFactory.createGoQuorumEnclave(new URI(""));

    goQuorumEnclave.sendSignedTransaction(transaction.getData().get().toArray(), rawTxArgs.getPrivateFor());

    return true;
  }


 JsonRpcErrorResponse getJsonRpcErrorResponse(
      final Object id, final TransactionInvalidReason errorReason) {
    if (errorReason.equals(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT)) {
      return new JsonRpcErrorResponse(id, JsonRpcError.PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT);
    }
    return new JsonRpcErrorResponse(id, convertTransactionInvalidReason(errorReason));
  }
}
