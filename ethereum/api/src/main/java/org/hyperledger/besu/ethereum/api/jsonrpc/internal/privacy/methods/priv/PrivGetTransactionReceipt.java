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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivGetTransactionReceipt implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final PrivacyParameters privacyParameters;
  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public PrivGetTransactionReceipt(
      final BlockchainQueries blockchain,
      final PrivacyParameters privacyParameters,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.blockchain = blockchain;
    this.privacyParameters = privacyParameters;
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_TRANSACTION_RECEIPT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_TRANSACTION_RECEIPT.getMethodName());
    final Hash pmtTransactionHash = requestContext.getRequiredParameter(0, Hash.class);
    final Optional<TransactionLocation> maybeLocation =
        blockchain.getBlockchain().getTransactionLocation(pmtTransactionHash);
    if (maybeLocation.isEmpty()) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
    final TransactionLocation pmtLocation = maybeLocation.get();
    final BlockBody blockBody =
        blockchain.getBlockchain().getBlockBody(pmtLocation.getBlockHash()).get();
    final Transaction pmtTransaction =
        blockBody.getTransactions().get(pmtLocation.getTransactionIndex());

    final Hash blockHash = pmtLocation.getBlockHash();
    final long blockNumber = blockchain.getBlockchain().getBlockHeader(blockHash).get().getNumber();

    final String payloadKey = pmtTransaction.getPayload().slice(0, 32).toBase64String();
    final String enclaveKey = enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());

    final Optional<PrivateTransaction> privateTransactionOptional;
    try {
      privateTransactionOptional =
          findPrivateTransactionInEnclave(
              payloadKey, pmtTransactionHash, enclaveKey, pmtLocation.getBlockHash());
    } catch (final EnclaveClientException e) {
      return handleEnclaveException(requestContext, e);
    }

    final String privacyGroupId;
    if (privateTransactionOptional.isPresent()) {
      final PrivateTransaction privateTransaction = privateTransactionOptional.get();
      try {
        if (privateTransaction.getPrivacyGroupId().isPresent()) {
          privacyGroupId = privateTransaction.getPrivacyGroupId().get().toBase64String();
        } else {
          privacyGroupId =
              privacyController.retrieveTransaction(payloadKey, enclaveKey).getPrivacyGroupId();
        }
      } catch (final EnclaveClientException e) {
        return handleEnclaveException(requestContext, e);
      }
      final String contractAddress =
          privateTransaction.getTo().isEmpty()
              ? Address.privateContractAddress(
                      privateTransaction.getSender(),
                      privateTransaction.getNonce(),
                      Bytes.fromBase64String(privacyGroupId))
                  .toString()
              : null;

      LOG.trace("Calculated contractAddress: {}", contractAddress);

      final PrivateStateStorage privateStateStorage = privacyParameters.getPrivateStateStorage();
      final PrivateTransactionReceipt privateTransactionReceipt =
          privateStateStorage
              .getTransactionReceipt(blockHash, pmtTransactionHash)
              // backwards compatibility - private receipts indexed by private transaction hash key
              .or(
                  () ->
                      findPrivateReceiptByPrivateTxHash(
                          privateStateStorage, blockHash, privateTransaction))
              .orElse(PrivateTransactionReceipt.FAILED);

      LOG.trace("Processed private transaction receipt");

      final PrivateTransactionReceiptResult result =
          new PrivateTransactionReceiptResult(
              contractAddress,
              privateTransaction.getSender().toString(),
              privateTransaction.getTo().map(Address::toString).orElse(null),
              privateTransactionReceipt.getLogs(),
              privateTransactionReceipt.getOutput(),
              blockHash,
              blockNumber,
              pmtLocation.getTransactionIndex(),
              pmtTransaction.getHash(),
              privateTransaction.getHash(),
              privateTransaction.getPrivateFrom(),
              privateTransaction.getPrivateFor().orElse(null),
              privateTransaction.getPrivacyGroupId().orElse(null),
              privateTransactionReceipt.getRevertReason().orElse(null),
              Quantity.create(privateTransactionReceipt.getStatus()));

      LOG.trace("Created Private Transaction Receipt Result from given Transaction Hash");

      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
    } else {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
  }

  private Optional<? extends PrivateTransactionReceipt> findPrivateReceiptByPrivateTxHash(
      final PrivateStateStorage privateStateStorage,
      final Hash blockHash,
      final PrivateTransaction privateTransaction) {
    final Bytes rlpEncoded = RLP.encode(privateTransaction::writeTo);
    final Bytes32 txHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpEncoded);

    return privateStateStorage.getTransactionReceipt(blockHash, txHash);
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
      if (e.getMessage().equals("EnclavePayloadNotFound")) {
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
        privacyParameters.getPrivateStateStorage().getPrivacyGroupHeadBlockMap(blockHash);
    if (privacyGroupHeadBlockMapOptional.isPresent()) {
      for (final Bytes32 privacyGroupId : privacyGroupHeadBlockMapOptional.get().keySet()) {
        final Optional<Bytes32> addDataKey =
            privacyParameters.getPrivateStateStorage().getAddDataKey(privacyGroupId);
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

  private JsonRpcResponse handleEnclaveException(
      final JsonRpcRequestContext requestContext, final EnclaveClientException e) {
    final JsonRpcError jsonRpcError =
        JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage());
    switch (jsonRpcError) {
      case ENCLAVE_PAYLOAD_NOT_FOUND:
        {
          return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
        }
      case ENCLAVE_KEYS_CANNOT_DECRYPT_PAYLOAD:
        {
          LOG.warn(
              "Unable to decrypt payload with configured privacy node key. Check if your 'privacy-public-key-file' property matches your Orion node public key.");
        }
        // fall through
      default:
        throw e;
    }
  }
}
