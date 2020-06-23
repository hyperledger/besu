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

import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class EeaSendRawTransaction implements JsonRpcMethod {

  private final TransactionPool transactionPool;
  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  /*
   Temporarily adding this flag to this method to avoid being able to use offchain and onchain
   privacy groups at the same time. Later on this check will be done in a better place.
  */
  private final boolean onchainPrivacyGroupsEnabled;

  public EeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider,
      final boolean onchainPrivacyGroupsEnabled) {
    this.transactionPool = transactionPool;
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
    this.onchainPrivacyGroupsEnabled = onchainPrivacyGroupsEnabled;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);

    try {
      final PrivateTransaction privateTransaction =
          PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

      final String enclavePublicKey =
          enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());

      if (!privateTransaction.getPrivateFrom().equals(Bytes.fromBase64String(enclavePublicKey))) {
        return new JsonRpcErrorResponse(id, PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
      }

      Optional<PrivacyGroup> maybePrivacyGroup = null;
      final Optional<Bytes> maybePrivacyGroupId = privateTransaction.getPrivacyGroupId();
      if (onchainPrivacyGroupsEnabled) {
        if (!maybePrivacyGroupId.isPresent()) {
          return new JsonRpcErrorResponse(id, JsonRpcError.ONCHAIN_PRIVACY_GROUP_ID_NOT_AVAILABLE);
        }
        maybePrivacyGroup =
            privacyController.retrieveOnChainPrivacyGroup(
                maybePrivacyGroupId.get(), enclavePublicKey);
        if (maybePrivacyGroup.isEmpty()
            && !privacyController.isGroupAdditionTransaction(privateTransaction)) {
          return new JsonRpcErrorResponse(id, JsonRpcError.ONCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);
        }
      } else { // !onchainPirvacyGroupEnabled
        if (maybePrivacyGroupId.isPresent()) {
          maybePrivacyGroup =
              privacyController.retrieveOffChainPrivacyGroup(
                  maybePrivacyGroupId.get().toBase64String(), enclavePublicKey);
        } else {
          maybePrivacyGroup = Optional.empty();
        }
      }

      final ValidationResult<TransactionInvalidReason> validationResult =
          privacyController.validatePrivateTransaction(privateTransaction, enclavePublicKey);
      if (!validationResult.isValid()) {
        return new JsonRpcErrorResponse(
            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
      }

      final String enclaveKey =
          privacyController.sendTransaction(
              privateTransaction, enclavePublicKey, maybePrivacyGroup);

      if (onchainPrivacyGroupsEnabled) {
        final Bytes privacyGroupId =
            maybePrivacyGroupId.orElseThrow(
                () ->
                    new RuntimeException(
                        JsonRpcError.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST.getMessage()));
        final Optional<String> addPayloadEnclaveKey =
            privacyController.buildAndSendAddPayload(
                privateTransaction,
                Bytes32.wrap(privacyGroupId),
                enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));
        return createPMTAndAddToTxPool(
            id,
            privateTransaction,
            buildCompoundKey(enclaveKey, addPayloadEnclaveKey),
            Address.ONCHAIN_PRIVACY);
      } else { // legacy or pantheon transaction
        return createPMTAndAddToTxPool(id, privateTransaction, enclaveKey, Address.DEFAULT_PRIVACY);
      }
    } catch (final IllegalArgumentException | RLPException e) {
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      final String message = e.getMessage();
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(message));
    }
  }

  JsonRpcResponse createPMTAndAddToTxPool(
      final Object id,
      final PrivateTransaction privateTransaction,
      final String payload,
      final Address privacyPrecompileAddress) {
    final Transaction privacyMarkerTransaction;
    privacyMarkerTransaction =
        privacyController.createPrivacyMarkerTransaction(
            payload, privateTransaction, privacyPrecompileAddress);
    return transactionPool
        .addLocalTransaction(privacyMarkerTransaction)
        .either(
            () -> new JsonRpcSuccessResponse(id, privacyMarkerTransaction.getHash().toString()),
            errorReason -> getJsonRpcErrorResponse(id, errorReason));
  }

  JsonRpcErrorResponse getJsonRpcErrorResponse(
      final Object id, final TransactionInvalidReason errorReason) {
    if (errorReason.equals(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT)) {
      return new JsonRpcErrorResponse(id, JsonRpcError.PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT);
    }
    return new JsonRpcErrorResponse(id, convertTransactionInvalidReason(errorReason));
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
