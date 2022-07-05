/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.RequiredBlocksPeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardChain;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeBesuControllerBuilder extends BesuControllerBuilder {
  private final AtomicReference<SyncState> syncState = new AtomicReference<>();
  private static final Logger LOG = LoggerFactory.getLogger(MergeBesuControllerBuilder.class);

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    return createTransitionMiningCoordinator(
        protocolSchedule,
        protocolContext,
        transactionPool,
        miningParameters,
        syncState,
        ethProtocolManager,
        new BackwardSyncContext(
            protocolContext,
            protocolSchedule,
            metricsSystem,
            ethProtocolManager.ethContext(),
            syncState,
            BackwardChain.from(
                storageProvider, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule))));
  }

  protected MiningCoordinator createTransitionMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager,
      final BackwardSyncContext backwardSyncContext) {

    this.syncState.set(syncState);

    return new MergeCoordinator(
        protocolContext,
        protocolSchedule,
        transactionPool.getPendingTransactions(),
        miningParameters,
        backwardSyncContext);
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return MergeProtocolSchedule.create(
        configOptionsSupplier.get(), privacyParameters, isRevertReasonEnabled);
  }

  @Override
  protected MergeContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {

    OptionalLong terminalBlockNumber = configOptionsSupplier.get().getTerminalBlockNumber();
    Optional<Hash> terminalBlockHash = configOptionsSupplier.get().getTerminalBlockHash();

    final MergeContext mergeContext =
        PostMergeContext.get()
            .setSyncState(syncState.get())
            .setTerminalTotalDifficulty(
                configOptionsSupplier
                    .get()
                    .getTerminalTotalDifficulty()
                    .map(Difficulty::of)
                    .orElse(Difficulty.ZERO));

    blockchain
        .getFinalized()
        .flatMap(blockchain::getBlockHeader)
        .ifPresent(mergeContext::setFinalized);

    blockchain
        .getSafeBlock()
        .flatMap(blockchain::getBlockHeader)
        .ifPresent(mergeContext::setSafeBlock);

    if (terminalBlockNumber.isPresent() && terminalBlockHash.isPresent()) {
      Optional<BlockHeader> termBlock = blockchain.getBlockHeader(terminalBlockNumber.getAsLong());
      mergeContext.setTerminalPoWBlock(termBlock);
    }
    blockchain.observeBlockAdded(
        blockAddedEvent ->
            blockchain
                .getTotalDifficultyByHash(blockAddedEvent.getBlock().getHeader().getHash())
                .ifPresent(mergeContext::setIsPostMerge));

    return mergeContext;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new NoopPluginServiceFactory();
  }

  @Override
  protected List<PeerValidator> createPeerValidators(final ProtocolSchedule protocolSchedule) {
    List<PeerValidator> retval = super.createPeerValidators(protocolSchedule);
    final OptionalLong powTerminalBlockNumber =
        configOptionsSupplier.get().getTerminalBlockNumber();
    final Optional<Hash> powTerminalBlockHash = configOptionsSupplier.get().getTerminalBlockHash();
    if (powTerminalBlockHash.isPresent() && powTerminalBlockNumber.isPresent()) {
      retval.add(
          new RequiredBlocksPeerValidator(
              protocolSchedule,
              metricsSystem,
              powTerminalBlockNumber.getAsLong(),
              powTerminalBlockHash.get(),
              0));
    } else {
      LOG.debug("unable to validate peers with terminal difficulty blocks");
    }
    return retval;
  }
}
