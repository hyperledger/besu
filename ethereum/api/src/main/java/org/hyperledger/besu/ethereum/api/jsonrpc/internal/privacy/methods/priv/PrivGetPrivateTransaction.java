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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivGetPrivateTransaction implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final PrivacyController privacyController;
  private final PrivateStateStorage privateStateStorage;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public PrivGetPrivateTransaction(
      final BlockchainQueries blockchain,
      final PrivacyController privacyController,
      final PrivateStateStorage privateStateStorage,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.blockchain = blockchain;
    this.privacyController = privacyController;
    this.privateStateStorage = privateStateStorage;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_PRIVATE_TRANSACTION.getMethodName());

    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final TransactionWithMetadata resultTransaction =
        blockchain.transactionByHash(hash).orElse(null);

    if (resultTransaction == null) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }

    final String payloadKey =
        resultTransaction.getTransaction().getPayload().slice(0, 32).toBase64String();
    final String enclaveKey = enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());
    final Optional<PrivateTransaction> privateTransaction;
    try {
      privateTransaction =
          findPrivateTransactionInEnclave(
              payloadKey,
              resultTransaction.getTransaction().getHash(),
              enclaveKey,
              resultTransaction.getBlockHash().get());
    } catch (final EnclaveClientException e) {
      if (e.getMessage().equals(JsonRpcError.ENCLAVE_PAYLOAD_NOT_FOUND.getMessage())) {
        return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
      } else {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(),
            JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
      }
    }

    if (privateTransaction.isPresent()) {
      if (privateTransaction.get().getPrivacyGroupId().isPresent()) {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new PrivateTransactionGroupResult(privateTransaction.get()));
      } else {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new PrivateTransactionLegacyResult(privateTransaction.get()));
      }
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);
    }
  }

  private Optional<PrivateTransaction> findPrivateTransactionInEnclave(
      final String payloadKey,
      final Hash pmtTransactionHash,
      final String enclaveKey,
      final Bytes32 blockHash) {
    PrivateTransaction privateTransaction;
    try {
      LOG.trace("Fetching transaction information");
      final ReceiveResponse receiveResponse =
          privacyController.retrieveTransaction(payloadKey, enclaveKey);

      final BytesValueRLPInput input =
          new BytesValueRLPInput(
              Bytes.fromBase64String(new String(receiveResponse.getPayload(), UTF_8)), false);
      input.enterList();
      if (input.nextIsList()) {
        privateTransaction = PrivateTransaction.readFrom(input);
        input.leaveListLenient();
      } else {
        input.reset();
        privateTransaction = PrivateTransaction.readFrom(input);
      }
      LOG.trace("Received transaction information");
    } catch (final EnclaveClientException e) {
      Optional<PrivateTransaction> privateTransactionOptional = Optional.empty();
      if (e.getMessage().equals(JsonRpcError.ENCLAVE_PAYLOAD_NOT_FOUND.getMessage())) {
        privateTransactionOptional = fetchPayloadFromAddBlob(blockHash, pmtTransactionHash);
      }
      if (privateTransactionOptional.isEmpty()) {
        throw e;
      } else {
        privateTransaction = privateTransactionOptional.get();
      }
    }
    return Optional.ofNullable(privateTransaction);
  }

  private Optional<PrivateTransaction> fetchPayloadFromAddBlob(
      final Bytes32 blockHash, final Hash expectedPrivacyMarkerTransactionHash) {
    LOG.trace("Fetching transaction information from add blob");
    final Optional<PrivacyGroupHeadBlockMap> privacyGroupHeadBlockMapOptional =
        privateStateStorage.getPrivacyGroupHeadBlockMap(blockHash);
    if (privacyGroupHeadBlockMapOptional.isPresent()) {
      for (final Bytes32 privacyGroupId : privacyGroupHeadBlockMapOptional.get().keySet()) {
        final Optional<Bytes32> addDataKey = privateStateStorage.getAddDataKey(privacyGroupId);
        if (addDataKey.isPresent()) {
          final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
              privacyController.retrieveAddBlob(addDataKey.get().toBase64String());
          for (final PrivateTransactionWithMetadata privateTransactionWithMetadata :
              privateTransactionWithMetadataList) {
            final Hash actualPrivacyMarkerTransactionHash =
                privateTransactionWithMetadata
                    .getPrivateTransactionMetadata()
                    .getPrivacyMarkerTransactionHash();
            if (expectedPrivacyMarkerTransactionHash.equals(actualPrivacyMarkerTransactionHash)) {
              return Optional.of(privateTransactionWithMetadata.getPrivateTransaction());
            }
          }
        }
      }
    }
    return Optional.empty();
  }
}
