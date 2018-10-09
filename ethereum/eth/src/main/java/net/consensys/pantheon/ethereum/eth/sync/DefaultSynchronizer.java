package net.consensys.pantheon.ethereum.eth.sync;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.SyncStatus;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import net.consensys.pantheon.ethereum.eth.sync.state.SyncState;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer<C> implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final SyncState syncState;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final BlockPropagationManager<C> blockPropagationManager;
  private final Downloader<C> downloader;

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext) {
    this.syncState =
        new SyncState(protocolContext.getBlockchain(), ethContext, new PendingBlocks());
    this.blockPropagationManager =
        new BlockPropagationManager<>(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState);
    this.downloader =
        new Downloader<>(syncConfig, protocolSchedule, protocolContext, ethContext, syncState);

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext, protocolSchedule, protocolContext.getBlockchain(), syncConfig);
    if (syncConfig.syncMode().equals(SyncMode.FAST)) {
      LOG.info("Fast sync enabled.");
    }
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      LOG.info("Starting synchronizer.");
      blockPropagationManager.start();
      downloader.start();
    } else {
      throw new IllegalStateException("Attempt to start an already started synchronizer.");
    }
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!started.get()) {
      return Optional.empty();
    }
    return Optional.of(syncState.syncStatus());
  }
}
