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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateStorageMigrationV2 implements PrivateStorageMigration {

  private static final Logger LOG = LogManager.getLogger();

  private final PrivateStateStorage privateStateStorage;
  private final Blockchain blockchain;
  private final Address privacyPrecompileAddress;
  private final ProtocolSchedule<?> protocolSchedule;
  private final WorldStateArchive publicWorldStateArchive;

  public PrivateStorageMigrationV2(
      final PrivateStateStorage privateStateStorage,
      final Blockchain blockchain,
      final Address privacyPrecompileAddress,
      final ProtocolSchedule<?> protocolSchedule,
      final WorldStateArchive publicWorldStateArchive) {
    this.privateStateStorage = privateStateStorage;
    this.blockchain = blockchain;
    this.privacyPrecompileAddress = privacyPrecompileAddress;
    this.protocolSchedule = protocolSchedule;
    this.publicWorldStateArchive = publicWorldStateArchive;
  }

  @Override
  public void migratePrivateStorage() {
    final long migrationStartTimestamp = System.currentTimeMillis();
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();

    LOG.info("Migrating private storage database...");

    for (int blockNumber = 0; blockNumber <= chainHeadBlockNumber; blockNumber++) {
      final Block block =
          blockchain
              .getBlockByNumber(blockNumber)
              .orElseThrow(PrivateStorageMigrationException::new);
      final Hash blockHash = block.getHash();
      final BlockHeader blockHeader = block.getHeader();
      LOG.info("Processing block {} ({}/{})", blockHash, blockNumber, chainHeadBlockNumber);

      createPrivacyGroupHeadBlockMap(blockHeader);

      List<Transaction> pmtsInBlock = findPMTsInBlock(block);
      if (!pmtsInBlock.isEmpty()) {
        final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(blockNumber);
        final PrivateMigrationBlockProcessor privateMigrationBlockProcessor =
            new PrivateMigrationBlockProcessor(
                protocolSpec.getTransactionProcessor(),
                protocolSpec.getTransactionReceiptFactory(),
                protocolSpec.getBlockReward(),
                protocolSpec.getMiningBeneficiaryCalculator(),
                protocolSpec.isSkipZeroBlockRewards());

        final MutableWorldState publicWorldState =
            blockchain
                .getBlockHeader(blockHeader.getParentHash())
                .map(BlockHeader::getStateRoot)
                .flatMap(publicWorldStateArchive::getMutable)
                .orElseThrow(PrivateStorageMigrationException::new);

        final List<Transaction> transactions = block.getBody().getTransactions();
        // TODO truncate list of transactions up to last PMT (optimization)

        privateMigrationBlockProcessor.processBlock(
            blockchain, publicWorldState, blockHeader, transactions, block.getBody().getOmmers());
      }
    }

    privateStateStorage.updater().putDatabaseVersion(2).commit();

    final long migrationDuration = System.currentTimeMillis() - migrationStartTimestamp;
    LOG.info("Migration took {} seconds", migrationDuration / 1000.0);
  }

  private void createPrivacyGroupHeadBlockMap(final BlockHeader blockHeader) {
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockHash =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));

    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockHash)
        .commit();
  }

  private List<Transaction> findPMTsInBlock(final Block block) {
    return block.getBody().getTransactions().stream()
        .filter(tx -> tx.getTo().isPresent() && tx.getTo().get().equals(privacyPrecompileAddress))
        .collect(Collectors.toList());
  }
}
