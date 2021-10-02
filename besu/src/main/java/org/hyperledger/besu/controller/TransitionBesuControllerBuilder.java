package org.hyperledger.besu.controller;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.TransitionProtocolSchedule;
import org.hyperledger.besu.consensus.merge.blockcreation.TransitionCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class TransitionBesuControllerBuilder extends BesuControllerBuilder {
  private final MainnetBesuControllerBuilder mainnetBesuControllerBuilder;
  private final MergeBesuControllerBuilder mergeBesuControllerBuilder;

  public TransitionBesuControllerBuilder(
      final MainnetBesuControllerBuilder mainnetBesuControllerBuilder,
      final MergeBesuControllerBuilder mergeBesuControllerBuilder) {
    this.mainnetBesuControllerBuilder = mainnetBesuControllerBuilder;
    this.mergeBesuControllerBuilder = mergeBesuControllerBuilder;
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    return new TransitionCoordinator(
        mainnetBesuControllerBuilder.createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager),
        mergeBesuControllerBuilder.createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager));
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return new TransitionProtocolSchedule(
        mainnetBesuControllerBuilder.createProtocolSchedule(),
        mergeBesuControllerBuilder.createProtocolSchedule());
  }

  @Override
  protected Object createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    // never called for pow so always use merge version
    return MergeContext.get();
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new NoopPluginServiceFactory();
  }
}
