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
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class EeaSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final TransactionPool transactionPool;
  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public EeaSendRawTransaction(
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
    final String rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);
    final Object id = requestContext.getRequest().getId();

    try {
      final PrivateTransaction privateTransaction =
          PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

      final Optional<String> addPayloadEnclaveKey =
          privacyController.buildAndSendAddPayload(
              privateTransaction, enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));

      final String enclavePublicKey =
          enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());
      final ValidationResult<TransactionInvalidReason> validationResult =
          privacyController.validatePrivateTransaction(privateTransaction, enclavePublicKey);
      if (!validationResult.isValid()) {
        return new JsonRpcErrorResponse(
            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
      }

      final String enclaveKey =
          privacyController.sendTransaction(privateTransaction, enclavePublicKey);
      final Transaction privacyMarkerTransaction;
      if (privateTransaction.getPrivacyGroupId().isPresent()) {
        PrivacyGroup privacyGroup = null;
        try {
          privacyGroup =
              privacyController.retrievePrivacyGroup(
                  privateTransaction.getPrivacyGroupId().get().toBase64String(),
                  enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));
        } catch (final EnclaveClientException e) {
          // it is an onchain group
        }
        if (privacyGroup == null
            || !privacyGroup
                .getMembers()
                .contains(enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()))) {
          privacyMarkerTransaction =
              privacyController.createPrivacyMarkerTransaction(
                  buildCompoundKey(enclaveKey, addPayloadEnclaveKey),
                  privateTransaction,
                  Address.ONCHAIN_PRIVACY);
        } else {
          privacyMarkerTransaction =
              privacyController.createPrivacyMarkerTransaction(enclaveKey, privateTransaction);
        }
      } else {
        privacyMarkerTransaction =
            privacyController.createPrivacyMarkerTransaction(enclaveKey, privateTransaction);
      }

      return transactionPool
          .addLocalTransaction(privacyMarkerTransaction)
          .either(
              () -> new JsonRpcSuccessResponse(id, privacyMarkerTransaction.getHash().toString()),
              errorReason ->
                  new JsonRpcErrorResponse(id, convertTransactionInvalidReason(errorReason)));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(id, ENCLAVE_ERROR);
    } catch (final IllegalArgumentException | RLPException e) {
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  private String buildCompoundKey(
      final String enclaveKey, final Optional<String> addPayloadEnclaveKey) {
    return addPayloadEnclaveKey.isPresent()
        ? Bytes.concatenate(
                Bytes.fromBase64String(enclaveKey),
                Bytes.fromBase64String(addPayloadEnclaveKey.get()))
            .toBase64String()
        : enclaveKey;
  }
}
