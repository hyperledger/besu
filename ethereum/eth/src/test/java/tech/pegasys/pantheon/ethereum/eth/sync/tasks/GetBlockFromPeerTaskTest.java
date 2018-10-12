package net.consensys.pantheon.ethereum.eth.sync.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import net.consensys.pantheon.ethereum.eth.manager.EthTask;
import net.consensys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import net.consensys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import net.consensys.pantheon.ethereum.eth.manager.ethtaskutils.AbstractMessageTaskTest;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.EthTaskException.FailureReason;
import net.consensys.pantheon.util.ExceptionUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class GetBlockFromPeerTaskTest
    extends AbstractMessageTaskTest<Block, PeerTaskResult<Block>> {

  @Override
  protected Block generateDataToBeRequested() {
    final BlockHeader header = blockchain.getBlockHeader(5).get();
    final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
    return new Block(header, body);
  }

  @Override
  protected EthTask<PeerTaskResult<Block>> createTask(final Block requestedData) {
    return GetBlockFromPeerTask.create(protocolSchedule, ethContext, requestedData.getHash());
  }

  @Override
  protected void assertResultMatchesExpectation(
      final Block requestedData,
      final PeerTaskResult<Block> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult()).isEqualTo(requestedData);
    assertThat(response.getPeer()).isEqualTo(respondingPeer);
  }

  @Test
  public void failsWhenNoPeersAreAvailable() throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final Block requestedData = generateDataToBeRequested();

    // Execute task
    final EthTask<PeerTaskResult<Block>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<Block>> future = task.run();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    future.whenComplete(
        (r, t) -> {
          failure.set(t);
        });

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(failure.get()).isNotNull();
    // Check wrapped failure
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    final EthTaskException ethException = (EthTaskException) error;
    assertThat(ethException.reason()).isEqualTo(FailureReason.NO_AVAILABLE_PEERS);

    assertThat(task.run().isCompletedExceptionally()).isTrue();
    task.cancel();
    assertThat(task.run().isCompletedExceptionally()).isTrue();
  }

  @Test
  public void failsWhenPeersSendEmptyResponses() throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data to be requested
    final Block requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<PeerTaskResult<Block>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<Block>> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(failure.get()).isNotNull();
    // Check wrapped failure
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    assertThat(((EthTaskException) error).reason()).isEqualTo(FailureReason.INCOMPLETE_RESULTS);

    assertThat(task.run().isCompletedExceptionally()).isTrue();
    task.cancel();
    assertThat(task.run().isCompletedExceptionally()).isTrue();
  }
}
