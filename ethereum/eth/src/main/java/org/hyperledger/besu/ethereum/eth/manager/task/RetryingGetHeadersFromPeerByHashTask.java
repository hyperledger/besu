package org.hyperledger.besu.ethereum.eth.manager.task;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RetryingGetHeadersFromPeerByHashTask
    extends AbstractRetryingPeerTask<List<BlockHeader>> {

  private final ProtocolSchedule protocolSchedule;
  private final Hash referenceHash;
  private final int count;
  private final int skip;

  private RetryingGetHeadersFromPeerByHashTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int maxRetries,
      final Hash referenceHash,
      final int count,
      final int skip,
      final MetricsSystem metricsSystem) {
    super(ethContext, maxRetries, List::isEmpty, metricsSystem);
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
    this.count = count;
    this.skip = skip;
    this.protocolSchedule = protocolSchedule;
  }

  public static RetryingGetHeadersFromPeerByHashTask startingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int maxRetries,
      final Hash referenceHash,
      final int count,
      final int skip,
      final MetricsSystem metricsSystem) {
    return new RetryingGetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, maxRetries, referenceHash, count, skip, metricsSystem);
  }

  @Override
  protected CompletableFuture<List<BlockHeader>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    AbstractGetHeadersFromPeerTask task =
        GetHeadersFromPeerByHashTask.startingAtHash(
            protocolSchedule, getEthContext(), referenceHash, count, skip, getMetricsSystem());
    assignedPeer.ifPresent(task::assignPeer);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }
}
