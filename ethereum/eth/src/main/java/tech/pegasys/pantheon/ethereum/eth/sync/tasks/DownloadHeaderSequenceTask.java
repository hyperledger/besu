package net.consensys.pantheon.ethereum.eth.sync.tasks;

import static java.util.Arrays.asList;
import static net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import net.consensys.pantheon.ethereum.eth.manager.AbstractRetryingPeerTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import net.consensys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import net.consensys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.google.common.primitives.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Retrieves a sequence of headers, sending out requests repeatedly until all headers are fulfilled.
 * Validates headers as they are received.
 *
 * @param <C> the consensus algorithm context
 */
public class DownloadHeaderSequenceTask<C> extends AbstractRetryingPeerTask<List<BlockHeader>> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private final ProtocolContext<C> protocolContext;
  private final ProtocolSchedule<C> protocolSchedule;

  private final BlockHeader[] headers;
  private final BlockHeader referenceHeader;
  private final int segmentLength;
  private final long startingBlockNumber;

  private int lastFilledHeaderIndex;

  private DownloadHeaderSequenceTask(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final BlockHeader referenceHeader,
      final int segmentLength) {
    super(ethContext);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.referenceHeader = referenceHeader;
    this.segmentLength = segmentLength;

    startingBlockNumber = referenceHeader.getNumber() - segmentLength;
    headers = new BlockHeader[segmentLength];
    lastFilledHeaderIndex = segmentLength;
  }

  public static <C> DownloadHeaderSequenceTask<C> endingAtHeader(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final BlockHeader referenceHeader,
      final int segmentLength) {
    return new DownloadHeaderSequenceTask<>(
        protocolSchedule, protocolContext, ethContext, referenceHeader, segmentLength);
  }

  @Override
  protected CompletableFuture<?> executePeerTask() {
    LOG.info(
        "Downloading headers from {} to {}.", startingBlockNumber, referenceHeader.getNumber() - 1);
    final CompletableFuture<List<BlockHeader>> task =
        downloadHeaders().thenCompose(this::processHeaders);
    return task.whenComplete(
        (r, t) -> {
          // We're done if we've filled all requested headers
          if (r != null && r.size() == segmentLength) {
            LOG.info(
                "Finished downloading headers from {} to {}.",
                startingBlockNumber,
                referenceHeader.getNumber() - 1);
            result.get().complete(Arrays.asList(headers));
          }
        });
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return error instanceof NoAvailablePeersException
        || error instanceof TimeoutException
        || error instanceof PeerBreachedProtocolException
        || error instanceof PeerDisconnectedException;
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeaders() {
    // Figure out parameters for our headers request
    final boolean partiallyFilled = lastFilledHeaderIndex < segmentLength;
    final BlockHeader referenceHeaderForNextRequest =
        partiallyFilled ? headers[lastFilledHeaderIndex] : referenceHeader;
    final Hash referenceHash = referenceHeaderForNextRequest.getHash();
    final int count = partiallyFilled ? lastFilledHeaderIndex : segmentLength;

    return executeSubTask(
        () -> {
          // Ask for count + 1 because we'll retrieve the previous header as well
          final AbstractGetHeadersFromPeerTask headersTask =
              GetHeadersFromPeerByHashTask.endingAtHash(
                  protocolSchedule,
                  ethContext,
                  referenceHash,
                  referenceHeaderForNextRequest.getNumber(),
                  count + 1);
          return headersTask.run();
        });
  }

  private CompletableFuture<List<BlockHeader>> processHeaders(
      final PeerTaskResult<List<BlockHeader>> headersResult) {
    return executeWorkerSubTask(
        ethContext.getScheduler(),
        () -> {
          final CompletableFuture<List<BlockHeader>> future = new CompletableFuture<>();
          BlockHeader child = null;
          boolean firstSkipped = false;
          for (final BlockHeader header : headersResult.getResult()) {
            final int headerIndex =
                Ints.checkedCast(
                    segmentLength - (referenceHeader.getNumber() - header.getNumber()));
            if (!firstSkipped) {
              // Skip over reference header
              firstSkipped = true;
              continue;
            }
            if (child == null) {
              child =
                  (headerIndex == segmentLength - 1) ? referenceHeader : headers[headerIndex + 1];
            }

            if (!validateHeader(child, header)) {
              // Invalid headers - disconnect from peer
              LOG.info(
                  "Received invalid headers from peer, disconnecting from: {}",
                  headersResult.getPeer());
              headersResult.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
              future.completeExceptionally(
                  new InvalidBlockException(
                      "Invalid header", header.getNumber(), header.getHash()));
              return future;
            }
            headers[headerIndex] = header;
            lastFilledHeaderIndex = headerIndex;
            child = header;
          }
          future.complete(asList(headers).subList(lastFilledHeaderIndex, segmentLength));
          return future;
        });
  }

  private boolean validateHeader(final BlockHeader child, final BlockHeader header) {
    final long finalBlockNumber = startingBlockNumber + segmentLength;
    final boolean blockInRange =
        header.getNumber() >= startingBlockNumber && header.getNumber() < finalBlockNumber;
    if (!blockInRange) {
      return false;
    }
    if (child == null) {
      return false;
    }

    final ProtocolSpec<C> protocolSpec = protocolSchedule.getByBlockNumber(child.getNumber());
    final BlockHeaderValidator<C> blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
    return blockHeaderValidator.validateHeader(child, header, protocolContext, DETACHED_ONLY);
  }
}
