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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRehydration;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivacyBlockProcessor implements BlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(PrivacyBlockProcessor.class);

  private final BlockProcessor blockProcessor;
  private final ProtocolSchedule protocolSchedule;
  private final Enclave enclave;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final PrivateStateGenesisAllocator privateStateGenesisAllocator;
  private WorldStateArchive publicWorldStateArchive;

  public PrivacyBlockProcessor(
      final BlockProcessor blockProcessor,
      final ProtocolSchedule protocolSchedule,
      final Enclave enclave,
      final PrivateStateStorage privateStateStorage,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final PrivateStateGenesisAllocator privateStateGenesisAllocator) {
    this.blockProcessor = blockProcessor;
    this.protocolSchedule = protocolSchedule;
    this.enclave = enclave;
    this.privateStateStorage = privateStateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateStateGenesisAllocator = privateStateGenesisAllocator;
  }

  public void setPublicWorldStateArchive(final WorldStateArchive publicWorldStateArchive) {
    this.publicWorldStateArchive = publicWorldStateArchive;
  }

  @Override
  public BlockProcessingResult processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers,
      final Optional<List<Withdrawal>> withdrawals,
      final PrivateMetadataUpdater privateMetadataUpdater) {

    if (privateMetadataUpdater != null) {
      throw new IllegalArgumentException("PrivateMetadataUpdater passed in is not null.");
    }

    maybeRehydrate(blockchain, blockHeader, transactions);

    final PrivateMetadataUpdater metadataUpdater =
        new PrivateMetadataUpdater(blockHeader, privateStateStorage);

    final BlockProcessingResult result =
        blockProcessor.processBlock(
            blockchain,
            worldState,
            blockHeader,
            transactions,
            ommers,
            withdrawals,
            metadataUpdater);
    metadataUpdater.commit();
    return result;
  }

  void maybeRehydrate(
      final Blockchain blockchain,
      final BlockHeader blockHeader,
      final List<Transaction> transactions) {
    transactions.stream()
        .filter(this::onchainAddToGroupPrivateMarkerTransactions)
        .forEach(
            pmt -> {
              final Bytes32 privateTransactionsLookupId =
                  Bytes32.wrap(pmt.getPayload().slice(32, 32));
              try {
                final ReceiveResponse receiveResponse =
                    enclave.receive(privateTransactionsLookupId.toBase64String());
                final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
                    PrivateTransactionWithMetadata.readListFromPayload(
                        Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())));
                final Bytes32 privacyGroupId =
                    Bytes32.wrap(
                        privateTransactionWithMetadataList
                            .get(0)
                            .getPrivateTransaction()
                            .getPrivacyGroupId()
                            .get());

                final List<PrivateTransactionWithMetadata> actualListToRehydrate =
                    transactionsInGroupThatNeedToBeApplied(
                        blockHeader, privateTransactionWithMetadataList, privacyGroupId);

                if (actualListToRehydrate.size() > 0) {
                  LOG.debug(
                      "Rehydrating privacy group {}, number of transactions to be rehydrated is {} out of a total number of {} transactions.",
                      privacyGroupId.toString(),
                      actualListToRehydrate.size(),
                      privateTransactionWithMetadataList.size());
                  final PrivateStateRehydration privateStateRehydration =
                      new PrivateStateRehydration(
                          privateStateStorage,
                          blockchain,
                          protocolSchedule,
                          publicWorldStateArchive,
                          privateWorldStateArchive,
                          privateStateRootResolver,
                          privateStateGenesisAllocator);
                  privateStateRehydration.rehydrate(actualListToRehydrate);
                  privateStateStorage
                      .updater()
                      .putAddDataKey(privacyGroupId, privateTransactionsLookupId)
                      .commit();
                }
              } catch (final EnclaveClientException e) {
                // we were not being added because we have not found the add blob
              }
            });
  }

  private boolean onchainAddToGroupPrivateMarkerTransactions(final Transaction t) {
    return t.getTo().isPresent()
        && t.getTo().equals(Optional.of(FLEXIBLE_PRIVACY))
        && t.getPayload().size() == 64;
  }

  private List<PrivateTransactionWithMetadata> transactionsInGroupThatNeedToBeApplied(
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
          final Hash nodeLatestPrivateMarkerTransactionHash =
              nodeLatestPrivateTxMetadataList
                  .get(nodeLatestPrivateTxMetadataList.size() - 1)
                  .getPrivateMarkerTransactionHash();
          for (int i = 0; i < privateTransactionWithMetadataList.size(); i++) {
            if (!privateTransactionWithMetadataList
                .get(i)
                .getPrivateTransactionMetadata()
                .getPrivateMarkerTransactionHash()
                .equals(nodeLatestPrivateMarkerTransactionHash)) {
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
}
