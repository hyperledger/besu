package org.hyperledger.besu.services;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.sync.WorldStateConfiguration;

import java.util.Collections;
import java.util.Optional;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class SynchronizationServiceImpl implements SynchronizationService {

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;

  private final SyncState syncState;
  private final BonsaiWorldStateProvider
      worldStateArchive; // TODO check bonsai activated for this plugin
  private final BesuController besuController;

  public SynchronizationServiceImpl(
      final BesuController besuController,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final SyncState syncState,
      final WorldStateArchive worldStateArchive) {
    this.besuController = besuController;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.syncState = syncState;
    this.worldStateArchive = (BonsaiWorldStateProvider) worldStateArchive;
  }

  @Override
  public BlockHeader getHead() {
    return protocolContext.getBlockchain().getChainHeadHeader();
  }

  @Override
  public boolean setHead(final BlockHeader blockHeader, final BlockBody blockBody) {
    final BlockImporter blockImporter =
        protocolSchedule
            .getByBlockHeader((org.hyperledger.besu.ethereum.core.BlockHeader) blockHeader)
            .getBlockImporter();
    return blockImporter
        .importBlock(
            protocolContext,
            new Block(
                (org.hyperledger.besu.ethereum.core.BlockHeader) blockHeader,
                (org.hyperledger.besu.ethereum.core.BlockBody) blockBody),
            HeaderValidationMode.SKIP_DETACHED)
        .isImported();
  }

  @Override
  public boolean setHeadUnsafe(final BlockHeader blockHeader, final BlockBody blockBody) {
    final org.hyperledger.besu.ethereum.core.BlockHeader coreHeader =
        (org.hyperledger.besu.ethereum.core.BlockHeader) blockHeader;
    final org.hyperledger.besu.ethereum.core.BlockBody coreBody =
        (org.hyperledger.besu.ethereum.core.BlockBody) blockBody;

    // move blockchain
    protocolContext
        .getBlockchain()
        .appendBlock(new Block(coreHeader, coreBody), Collections.emptyList());

    final Optional<MutableWorldState> worldState = worldStateArchive.getMutable(coreHeader, true);
    return worldState.isPresent();
  }

  @Override
  public boolean isInitialSyncPhaseDone() {
    return syncState.isInitialSyncPhaseDone();
  }

  @Override
  public void disableSynchronization() {
    protocolContext.getSynchronizer().ifPresent(Synchronizer::stop);
    besuController.setMiningCoordinator(
        new NoopMiningCoordinator(
            besuController.getMiningCoordinator().getMinTransactionGasPrice(),
            besuController.getMiningCoordinator().getCoinbase()));
  }

  @Override
  public void setWorldStateConfiguration(final WorldStateConfiguration worldStateConfiguration) {
    worldStateArchive
        .getDefaultBonsaiWorldStateConfig()
        .setTrieDisabled(worldStateConfiguration.isTrieDisabled());
    if (worldStateConfiguration.isTrieDisabled()) {
      worldStateArchive.disableTrie();
    }
  }
}
