package net.consensys.pantheon.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import net.consensys.pantheon.ethereum.eth.manager.AbstractRetryingPeerTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Given a set of headers, "completes" them by repeatedly requesting additional data (bodies) needed
 * to create the blocks that correspond to the supplied headers.
 *
 * @param <C> the consensus algorithm context
 */
public class CompleteBlocksTask<C> extends AbstractRetryingPeerTask<List<Block>> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private final ProtocolSchedule<C> protocolSchedule;

  private final List<BlockHeader> headers;
  private final Map<Long, Block> blocks;
  private Optional<EthPeer> assignedPeer = Optional.empty();

  private CompleteBlocksTask(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers) {
    super(ethContext);
    checkArgument(headers.size() > 0, "Must supply a non-empty headers list");
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;

    this.headers = headers;
    this.blocks = new HashMap<>();
  }

  public static <C> CompleteBlocksTask<C> forHeaders(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers) {
    return new CompleteBlocksTask<>(protocolSchedule, ethContext, headers);
  }

  @Override
  protected CompletableFuture<?> executePeerTask() {
    return requestBodies().thenCompose(this::processBodiesResult);
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    final boolean isPeerError =
        error instanceof PeerBreachedProtocolException
            || error instanceof PeerDisconnectedException
            || error instanceof NoAvailablePeersException;

    return error instanceof TimeoutException || (!assignedPeer.isPresent() && isPeerError);
  }

  public CompleteBlocksTask<C> assignPeer(final EthPeer peer) {
    assignedPeer = Optional.of(peer);
    return this;
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> requestBodies() {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    LOG.info(
        "Requesting bodies to complete {} blocks, starting with {}.",
        incompleteHeaders.size(),
        incompleteHeaders.get(0).getNumber());
    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask<C> task =
              GetBodiesFromPeerTask.forHeaders(protocolSchedule, ethContext, incompleteHeaders);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run();
        });
  }

  private CompletableFuture<Void> processBodiesResult(
      final PeerTaskResult<List<Block>> blocksResult) {
    blocksResult
        .getResult()
        .forEach(
            (block) -> {
              blocks.put(block.getHeader().getNumber(), block);
            });

    final boolean done = incompleteHeaders().size() == 0;
    if (done) {
      result
          .get()
          .complete(
              headers.stream().map(h -> blocks.get(h.getNumber())).collect(Collectors.toList()));
    }

    final CompletableFuture<Void> future = new CompletableFuture<>();
    future.complete(null);
    return future;
  }

  private List<BlockHeader> incompleteHeaders() {
    return headers
        .stream()
        .filter(h -> blocks.get(h.getNumber()) == null)
        .collect(Collectors.toList());
  }
}
