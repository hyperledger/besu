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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateStateRehydration {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateStateRehydration.class);

  private final PrivateStateStorage privateStateStorage;
  private final Blockchain blockchain;
  private final ProtocolSchedule protocolSchedule;
  private final WorldStateArchive publicWorldStateArchive;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final PrivateStateGenesisAllocator privateStateGenesisAllocator;

  public PrivateStateRehydration(
      final PrivateStateStorage privateStateStorage,
      final Blockchain blockchain,
      final ProtocolSchedule protocolSchedule,
      final WorldStateArchive publicWorldStateArchive,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final PrivateStateGenesisAllocator privateStateGenesisAllocator) {
    this.privateStateStorage = privateStateStorage;
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
    this.publicWorldStateArchive = publicWorldStateArchive;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateStateGenesisAllocator = privateStateGenesisAllocator;
  }

  public void rehydrate(
      final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList) {
    final long rehydrationStartTimestamp = System.currentTimeMillis();
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final Optional<Bytes> maybeGroupId =
        privateTransactionWithMetadataList.get(0).getPrivateTransaction().getPrivacyGroupId();
    if (maybeGroupId.isEmpty()) {
      LOG.debug("Flexible groups must have a group id.");
      return;
    }
    final Bytes32 privacyGroupId = Bytes32.wrap(maybeGroupId.get());

    LOG.debug("Rehydrating privacy group {}", privacyGroupId.toBase64String());

    // check if there is a privacyGroupHeadBlockMap for the first block ...
    final boolean needEmptyPrivacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(
                getBlockHashForIndex(0, privateTransactionWithMetadataList))
            .isEmpty();
    if (needEmptyPrivacyGroupHeadBlockMap) {
      privateStateStorage
          .updater()
          .putPrivacyGroupHeadBlockMap(
              getBlockHashForIndex(0, privateTransactionWithMetadataList),
              PrivacyGroupHeadBlockMap.empty())
          .commit();
    }

    final LinkedHashMap<Hash, PrivateTransaction> pmtHashToPrivateTransactionMap =
        new LinkedHashMap<>();
    for (int j = 0; j < privateTransactionWithMetadataList.size(); j++) {
      final PrivateTransactionWithMetadata transactionWithMetadata =
          privateTransactionWithMetadataList.get(j);
      pmtHashToPrivateTransactionMap.put(
          transactionWithMetadata.getPrivateTransactionMetadata().getPrivateMarkerTransactionHash(),
          transactionWithMetadata.getPrivateTransaction());
    }

    for (int i = 0; i < privateTransactionWithMetadataList.size(); i++) {
      // find out which block this transaction is in
      final Hash blockHash = getBlockHashForIndex(i, privateTransactionWithMetadataList);

      // At the end of the while loop i will be the index of the last PMT (for this group) that is
      // in this block.
      while (i + 1 < privateTransactionWithMetadataList.size()
          && blockHash.equals(getBlockHashForIndex(i + 1, privateTransactionWithMetadataList))) {
        i++;
      }

      final Hash lastPmtHash =
          privateTransactionWithMetadataList
              .get(i)
              .getPrivateTransactionMetadata()
              .getPrivateMarkerTransactionHash();

      final Optional<TransactionLocation> transactionLocationOfLastPmtInBlock =
          blockchain.getTransactionLocation(lastPmtHash);
      if (transactionLocationOfLastPmtInBlock.isEmpty()) {
        LOG.debug("Rehydartion failed - missing marker transaction for {}", lastPmtHash);
        return;
      }

      final Block block = blockchain.getBlockByHash(blockHash).orElseThrow(RuntimeException::new);
      final BlockHeader blockHeader = block.getHeader();
      LOG.debug(
          "Rehydrating block {} ({}/{}), {}",
          blockHash,
          blockHeader.getNumber(),
          chainHeadBlockNumber,
          block.getBody().getTransactions().stream()
              .map(Transaction::getHash)
              .collect(Collectors.toList()));

      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(blockHeader.getNumber());
      final PrivateGroupRehydrationBlockProcessor privateGroupRehydrationBlockProcessor =
          new PrivateGroupRehydrationBlockProcessor(
              protocolSpec.getTransactionProcessor(),
              protocolSpec.getPrivateTransactionProcessor(),
              protocolSpec.getTransactionReceiptFactory(),
              protocolSpec.getBlockReward(),
              protocolSpec.getMiningBeneficiaryCalculator(),
              protocolSpec.isSkipZeroBlockRewards(),
              privateStateGenesisAllocator);

      final MutableWorldState publicWorldState =
          blockchain
              .getBlockHeader(blockHeader.getParentHash())
              .flatMap(
                  header ->
                      publicWorldStateArchive.getMutable(header.getStateRoot(), header.getHash()))
              .orElseThrow(RuntimeException::new);

      privateGroupRehydrationBlockProcessor.processBlock(
          blockchain,
          publicWorldState,
          privateWorldStateArchive,
          privateStateStorage,
          privateStateRootResolver,
          block,
          pmtHashToPrivateTransactionMap,
          block.getBody().getOmmers());

      // check the resulting private state against the state in the meta data
      final Optional<Hash> latestStateRoot =
          privateStateStorage
              .getPrivateBlockMetadata(blockHash, privacyGroupId)
              .orElseThrow()
              .getLatestStateRoot();
      if (latestStateRoot.isPresent()) {
        if (!latestStateRoot
            .get()
            .equals(
                privateTransactionWithMetadataList
                    .get(i)
                    .getPrivateTransactionMetadata()
                    .getStateRoot())) {
          throw new RuntimeException();
        }
      }
      // fix the privacy group header block map for the blocks between the current block and the
      // next block containing a pmt for this privacy group
      if (i + 1 < privateTransactionWithMetadataList.size()) {
        rehydratePrivacyGroupHeadBlockMap(
            privacyGroupId,
            blockHash,
            blockchain,
            getBlockNumberForIndex(i, privateTransactionWithMetadataList),
            getBlockNumberForIndex(i + 1, privateTransactionWithMetadataList));
      } else {
        rehydratePrivacyGroupHeadBlockMap(
            privacyGroupId,
            blockHash,
            blockchain,
            getBlockNumberForIndex(i, privateTransactionWithMetadataList),
            blockchain.getChainHeadBlockNumber() + 1);
      }
    }
    final long rehydrationDuration = System.currentTimeMillis() - rehydrationStartTimestamp;
    LOG.debug("Rehydration took {} seconds", rehydrationDuration / 1000.0);
  }

  protected void rehydratePrivacyGroupHeadBlockMap(
      final Bytes32 privacyGroupId,
      final Hash hashOfLastBlockWithPmt,
      final Blockchain currentBlockchain,
      final long from,
      final long to) {
    for (long j = from + 1; j < to; j++) {
      final BlockHeader theBlockHeader = currentBlockchain.getBlockHeader(j).orElseThrow();
      final PrivacyGroupHeadBlockMap thePrivacyGroupHeadBlockMap =
          privateStateStorage
              .getPrivacyGroupHeadBlockMap(theBlockHeader.getHash())
              .orElse(PrivacyGroupHeadBlockMap.empty());
      final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();
      thePrivacyGroupHeadBlockMap.put(privacyGroupId, hashOfLastBlockWithPmt);
      privateStateUpdater.putPrivacyGroupHeadBlockMap(
          theBlockHeader.getHash(), new PrivacyGroupHeadBlockMap(thePrivacyGroupHeadBlockMap));
      privateStateUpdater.commit();
    }
  }

  private long getBlockNumberForIndex(
      final int index,
      final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList) {
    return blockchain
        .getBlockHeader(getBlockHashForIndex(index, privateTransactionWithMetadataList))
        .orElseThrow()
        .getNumber();
  }

  private Hash getBlockHashForIndex(
      final int index,
      final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList) {
    return blockchain
        .getTransactionLocation(
            privateTransactionWithMetadataList
                .get(index)
                .getPrivateTransactionMetadata()
                .getPrivateMarkerTransactionHash())
        .orElseThrow()
        .getBlockHash();
  }
}
