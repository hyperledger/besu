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
package org.hyperledger.besu.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateTransactionLocator {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateTransactionLocator.class);

  private final Blockchain blockchain;
  private final Enclave enclave;
  private final PrivateStateStorage privateStateStorage;

  public PrivateTransactionLocator(
      final Blockchain blockchain,
      final Enclave enclave,
      final PrivateStateStorage privateStateStorage) {
    this.blockchain = blockchain;
    this.enclave = enclave;
    this.privateStateStorage = privateStateStorage;
  }

  /**
   * Returns a private transaction with extra data (block and pmt information). The private
   * transaction is retrieved from the Enclave, either as a single transaction or from an existing
   * "blob".<br>
   * In both cases, we are passing the enclaveKey (public key of the participant) down to the
   * Enclave, with the expectation that the Enclave will only return the payload if the matching
   * private key of the enclaveKey passed as a parameter is able to be used to decrypt the payload.
   *
   * <p>This enclaveKey "validation" in the Enclave makes retrieving a payload multi-tenancy safe.
   *
   * @param pmtHash the hash of the PMT associated with the private transaction
   * @param enclaveKey participant public key that must match the private key used to decrypt the
   *     payload
   * @return An executed private transaction
   */
  public Optional<ExecutedPrivateTransaction> findByPmtHash(
      final Hash pmtHash, final String enclaveKey) {
    final Optional<TransactionLocation> maybePmtLocation =
        blockchain.getTransactionLocation(pmtHash);
    if (maybePmtLocation.isEmpty()) {
      return Optional.empty();
    }

    final TransactionLocation pmtLocation = maybePmtLocation.get();
    final Transaction pmt = blockchain.getTransactionByHash(pmtHash).orElseThrow();
    final BlockHeader blockHeader =
        blockchain.getBlockHeader(pmtLocation.getBlockHash()).orElseThrow();
    final String payloadKey = readPayloadKeyFromPmt(pmt);

    return tryFetchingPrivateTransactionFromEnclave(payloadKey, enclaveKey)
        .or(() -> tryFetchingTransactionFromAddBlob(blockHeader.getHash(), pmtHash, enclaveKey))
        .map(
            tx ->
                new ExecutedPrivateTransaction(
                    blockHeader.getHash(),
                    blockHeader.getNumber(),
                    pmt.getHash(),
                    pmtLocation.getTransactionIndex(),
                    tx.getInternalPrivacyGroupId(),
                    tx.getPrivateTransaction()));
  }

  private String readPayloadKeyFromPmt(final Transaction privacyMarkerTx) {
    return privacyMarkerTx.getPayload().slice(0, 32).toBase64String();
  }

  /**
   * Retrieves a single private transaction from the Enclave
   *
   * @param payloadKey unique key identifying the payload
   * @param enclaveKey participant public key that must match the private key used to decrypt the
   *     payload
   * @return an optional containing the private transaction, if found. Or an empty optional if the
   *     private transaction couldn't be found.
   */
  private Optional<TransactionFromEnclave> tryFetchingPrivateTransactionFromEnclave(
      final String payloadKey, final String enclaveKey) {
    return retrievePayloadFromEnclave(payloadKey, enclaveKey)
        .map(this::readPrivateTransactionFromPayload);
  }

  private Optional<ReceiveResponse> retrievePayloadFromEnclave(
      final String payloadKey, final String enclaveKey) {
    try {
      return Optional.of(enclave.receive(payloadKey, enclaveKey));
    } catch (final EnclaveClientException e) {
      // Enclave throws an exception with a 404 status code if the payload isn't found
      if (e.getStatusCode() == 404) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  private TransactionFromEnclave readPrivateTransactionFromPayload(
      final ReceiveResponse receiveResponse) {
    final PrivateTransaction privateTransaction;
    final BytesValueRLPInput input =
        new BytesValueRLPInput(
            Bytes.fromBase64String(new String(receiveResponse.getPayload(), UTF_8)), false);

    /*
     When using onchain privacy groups, the payload is a list with the first element being the
     private transaction RLP and the second element being the version. This is why we have the
     nextIsList() check.
    */
    try {
      input.enterList();
      if (input.nextIsList()) {
        // private transaction and version (we only read the first element in the list)
        privateTransaction = PrivateTransaction.readFrom(input);
        input.leaveListLenient();
      } else {
        // private transaction only (read the whole RLP)
        input.reset();
        privateTransaction = PrivateTransaction.readFrom(input);
      }
    } catch (final RLPException e) {
      throw new IllegalStateException("Error de-serializing private transaction from enclave", e);
    }

    return new TransactionFromEnclave(privateTransaction, receiveResponse.getPrivacyGroupId());
  }

  private Optional<TransactionFromEnclave> tryFetchingTransactionFromAddBlob(
      final Bytes32 blockHash, final Hash expectedPmtHash, final String enclaveKey) {
    LOG.trace("Fetching transaction information from add blob");

    final Optional<PrivacyGroupHeadBlockMap> privacyGroupHeadBlockMapOptional =
        privateStateStorage.getPrivacyGroupHeadBlockMap(blockHash);

    if (privacyGroupHeadBlockMapOptional.isPresent()) {
      final Set<Bytes32> mappedPrivacyGroupIds = privacyGroupHeadBlockMapOptional.get().keySet();

      for (final Bytes32 privacyGroupId : mappedPrivacyGroupIds) {
        final Optional<Bytes32> addDataKey = privateStateStorage.getAddDataKey(privacyGroupId);

        if (addDataKey.isPresent()) {
          final String payloadKey = addDataKey.get().toBase64String();
          final Optional<ReceiveResponse> receiveResponse =
              retrievePayloadFromEnclave(payloadKey, enclaveKey);
          if (receiveResponse.isEmpty()) {
            LOG.warn(
                "Unable to find private transaction with payloadKey = {} on AddBlob", payloadKey);
            return Optional.empty();
          }

          final Bytes payload =
              Bytes.wrap(Base64.getDecoder().decode(receiveResponse.get().getPayload()));
          final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
              PrivateTransactionWithMetadata.readListFromPayload(payload);

          for (final PrivateTransactionWithMetadata privateTx :
              privateTransactionWithMetadataList) {
            final Hash actualPrivateMarkerTransactionHash =
                privateTx.getPrivateTransactionMetadata().getPrivateMarkerTransactionHash();

            if (expectedPmtHash.equals(actualPrivateMarkerTransactionHash)) {
              return Optional.of(
                  new TransactionFromEnclave(
                      privateTx.getPrivateTransaction(),
                      receiveResponse.get().getPrivacyGroupId()));
            }
          }
        }
      }
    }

    return Optional.empty();
  }

  private static class TransactionFromEnclave {
    private final PrivateTransaction privateTransaction;
    private final String internalPrivacyGroupId;

    public TransactionFromEnclave(
        final PrivateTransaction privateTransaction, final String internalPrivacyGroupId) {
      this.privateTransaction = privateTransaction;
      this.internalPrivacyGroupId = internalPrivacyGroupId;
    }

    public PrivateTransaction getPrivateTransaction() {
      return privateTransaction;
    }

    public String getInternalPrivacyGroupId() {
      return internalPrivacyGroupId;
    }
  }
}
