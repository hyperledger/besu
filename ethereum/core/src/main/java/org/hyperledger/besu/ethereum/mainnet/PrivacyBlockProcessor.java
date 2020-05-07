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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRehydration;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivacyBlockProcessor implements BlockProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockProcessor blockProcessor;
  private final ProtocolSchedule<?> protocolSchedule;
  private final Enclave enclave;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateRootResolver privateStateRootResolver;
  private WorldStateArchive publicWorldStateArchive;

  public <C> PrivacyBlockProcessor(
      final BlockProcessor blockProcessor,
      final ProtocolSchedule<C> protocolSchedule,
      final Enclave enclave,
      final PrivateStateStorage privateStateStorage,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver) {
    this.blockProcessor = blockProcessor;
    this.protocolSchedule = protocolSchedule;
    this.enclave = enclave;
    this.privateStateStorage = privateStateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
  }

  public void setPublicWorldStateArchive(final WorldStateArchive publicWorldStateArchive) {
    this.publicWorldStateArchive = publicWorldStateArchive;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {

    maybeRehydrate(blockchain, blockHeader, transactions);

    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockMap)
        .commit();
    return blockProcessor.processBlock(blockchain, worldState, blockHeader, transactions, ommers);
  }

  void maybeRehydrate(
      final Blockchain blockchain,
      final BlockHeader blockHeader,
      final List<Transaction> transactions) {
    transactions.stream()
        .filter(
            t ->
                t.getTo().isPresent()
                    && t.getTo().equals(Optional.of(Address.ONCHAIN_PRIVACY))
                    && t.getPayload().size() == 64)
        .forEach(
            t -> {
              final Bytes32 addKey = Bytes32.wrap(t.getPayload().slice(32, 32));
              try {
                final ReceiveResponse receiveResponse = enclave.receive(addKey.toBase64String());
                final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
                    deserializeAddToGroupPayload(
                        Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())));
                final Bytes32 privacyGroupId =
                    Bytes32.wrap(
                        privateTransactionWithMetadataList
                            .get(0)
                            .getPrivateTransaction()
                            .getPrivacyGroupId()
                            .get());

                final List<PrivateTransactionWithMetadata> actualList =
                    createActualList(
                        blockHeader, privateTransactionWithMetadataList, privacyGroupId);

                if (actualList.size() > 0) {
                  LOG.debug(
                      "Rehydrating privacy group {}, number of transactions to be rehydrated is {} out of a total number of {} transactions.",
                      privacyGroupId.toString(),
                      actualList.size(),
                      privateTransactionWithMetadataList.size());
                  final PrivateStateRehydration privateStateRehydration =
                      new PrivateStateRehydration(
                          privateStateStorage,
                          blockchain,
                          protocolSchedule,
                          publicWorldStateArchive,
                          privateWorldStateArchive,
                          privateStateRootResolver);
                  privateStateRehydration.rehydrate(actualList);
                  privateStateStorage.updater().putAddDataKey(privacyGroupId, addKey).commit();
                }
              } catch (final EnclaveClientException e) {
                // we were not being added because we have not found the add blob
              }
            });
  }

  private List<PrivateTransactionWithMetadata> createActualList(
      final BlockHeader blockHeader,
      final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList,
      final Bytes32 privacyGroupId) {
    // if we are the member adding another member we do not have to rehydrate
    // if we have been removed from the group at some point we only need to rehydrate from where we
    // were removed
    // if we are a new member we need to rehydrate the complete state

    List<PrivateTransactionWithMetadata> actualList = privateTransactionWithMetadataList;

    final Optional<PrivacyGroupHeadBlockMap> maybePrivacyGroupHeadBlockMap =
        privateStateStorage.getPrivacyGroupHeadBlockMap(blockHeader.getParentHash());
    if (maybePrivacyGroupHeadBlockMap.isPresent()) {
      final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap = maybePrivacyGroupHeadBlockMap.get();
      final Hash lastBlockWithTx = privacyGroupHeadBlockMap.get(privacyGroupId);
      if (lastBlockWithTx != null) {
        // we are or have been a member of the privacy group
        final PrivateBlockMetadata nodeLatestBlockMetadata =
            privateStateStorage
                .getPrivateBlockMetadata(lastBlockWithTx, privacyGroupId)
                .orElseThrow();
        final List<PrivateTransactionMetadata> nodeLatestPrivateTxMetadataList =
            nodeLatestBlockMetadata.getPrivateTransactionMetadataList();
        final Hash nodeLatestStateRoot =
            nodeLatestPrivateTxMetadataList
                .get(nodeLatestPrivateTxMetadataList.size() - 1)
                .getStateRoot();
        final Hash latestStateRootFromRehydrationList =
            privateTransactionWithMetadataList
                .get(privateTransactionWithMetadataList.size() - 1)
                .getPrivateTransactionMetadata()
                .getStateRoot();
        if (nodeLatestStateRoot.equals(latestStateRootFromRehydrationList)) {
          // we are already on the latest state root, which means that we are the member adding a
          // new member
          actualList = Collections.emptyList();
        } else {
          // we are being added, but do not have to rehydrate all private transactions
          final Hash nodeLatestPrivacyMarkerTransactionHash =
              nodeLatestPrivateTxMetadataList
                  .get(nodeLatestPrivateTxMetadataList.size() - 1)
                  .getPrivacyMarkerTransactionHash();
          for (int i = 0; i < privateTransactionWithMetadataList.size(); i++) {
            if (!privateTransactionWithMetadataList
                .get(i)
                .getPrivateTransactionMetadata()
                .getPrivacyMarkerTransactionHash()
                .equals(nodeLatestPrivacyMarkerTransactionHash)) {
              continue;
            }
            if (privateTransactionWithMetadataList.size() - 1 == i) {
              actualList = Collections.emptyList(); // nothing needs to be re-hydrated
            } else {
              actualList =
                  privateTransactionWithMetadataList.subList(
                      i + 1, privateTransactionWithMetadataList.size());
            }
            break;
          }
        }
      }
    }
    return actualList;
  }

  private List<PrivateTransactionWithMetadata> deserializeAddToGroupPayload(
      final Bytes encodedAddToGroupPayload) {
    final ArrayList<PrivateTransactionWithMetadata> deserializedResponse = new ArrayList<>();
    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(encodedAddToGroupPayload, false);
    final int noOfEntries = bytesValueRLPInput.enterList();
    for (int i = 0; i < noOfEntries; i++) {
      deserializedResponse.add(PrivateTransactionWithMetadata.readFrom(bytesValueRLPInput));
    }
    bytesValueRLPInput.leaveList();
    return deserializedResponse;
  }
}
