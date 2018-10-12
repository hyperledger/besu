package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByNumberTask extends AbstractGetHeadersFromPeerTask {
  private static final Logger LOG = LogManager.getLogger();

  private final long blockNumber;

  @VisibleForTesting
  GetHeadersFromPeerByNumberTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long blockNumber,
      final int count,
      final int skip,
      final boolean reverse) {
    super(protocolSchedule, ethContext, blockNumber, count, skip, reverse);
    this.blockNumber = blockNumber;
  }

  public static AbstractGetHeadersFromPeerTask startingAtNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long firstBlockNumber,
      final int segmentLength) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, firstBlockNumber, segmentLength, 0, false);
  }

  public static AbstractGetHeadersFromPeerTask endingAtNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long lastlockNumber,
      final int segmentLength) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, lastlockNumber, segmentLength, 0, true);
  }

  public static AbstractGetHeadersFromPeerTask endingAtNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long lastlockNumber,
      final int segmentLength,
      final int skip) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, lastlockNumber, segmentLength, skip, true);
  }

  public static AbstractGetHeadersFromPeerTask forSingleNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long blockNumber) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, blockNumber, 1, 0, false);
  }

  @Override
  protected ResponseStream sendRequest(final EthPeer peer) throws PeerNotConnected {
    LOG.info("Requesting {} headers from peer {}.", count, peer);
    return peer.getHeadersByNumber(blockNumber, count, reverse, skip);
  }

  @Override
  protected boolean matchesFirstHeader(final BlockHeader firstHeader) {
    return firstHeader.getNumber() == blockNumber;
  }
}
