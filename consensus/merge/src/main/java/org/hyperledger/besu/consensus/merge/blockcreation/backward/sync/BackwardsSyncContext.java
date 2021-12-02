package org.hyperledger.besu.consensus.merge.blockcreation.backward.sync;

import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BackwardsSyncContext {
  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final BlockValidator blockValidator;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  private final Map<Long, BackwardChain> backwardChainMap = new ConcurrentHashMap<>();
  AtomicReference<Block> currentPivot = new AtomicReference<>();

  public BackwardsSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final BlockValidator blockValidator) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.blockValidator = blockValidator;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Block pivot) {
    this.currentPivot.set(pivot);
    return new BackwardSyncStep(this, pivot)
        .executeAsync(null)
        .thenCompose(new ForwardSyncStep(this, pivot)::executeAsync);
  }

  public Optional<Block> getCurrentPivot() {
    return Optional.of(currentPivot.get());
  }

  public Map<Long, BackwardChain> getBackwardChainMap() {
    return backwardChainMap;
  }

  public ProtocolSchedule getProtocolSchedule() {
    return protocolSchedule;
  }

  public EthContext getEthContext() {
    return ethContext;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  public BlockValidator getBlockValidator() {
    return blockValidator;
  }
}
