package tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests ethTasks that request data from the network, and retry until all of the data is received.
 *
 * @param <T>
 */
public abstract class RetryingMessageTaskTest<T, R> extends AbstractMessageTaskTest<T, R> {

  @Test
  public void doesNotCompleteWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested and expected response

    // Setup a partially responsive peer
    final Responder responder = RespondingEthPeer.partialResponder(blockchain, protocolSchedule);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final T requestedData = generateDataToBeRequested();
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    respondingPeer.respondTimes(responder, 20);
    future.whenComplete(
        (result, error) -> {
          done.compareAndSet(false, true);
        });

    assertThat(done).isFalse();
  }

  @Test
  public void doesNotCompleteWhenPeersAreUnavailable()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    future.whenComplete(
        (result, error) -> {
          done.compareAndSet(false, true);
        });

    assertThat(done).isFalse();
  }

  @Test
  public void completesWhenPeersAreTemporarilyUnavailable()
      throws ExecutionException, InterruptedException, TimeoutException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final AtomicReference<R> actualResult = new AtomicReference<>();
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isFalse();

    // Setup a peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertResultMatchesExpectation(requestedData, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void completeWhenPeersTimeoutTemporarily()
      throws ExecutionException, InterruptedException, TimeoutException {
    peerCountToTimeout.set(1);
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final AtomicReference<R> actualResult = new AtomicReference<>();
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isFalse();
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertResultMatchesExpectation(requestedData, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void doesNotCompleteWhenPeersSendEmptyResponses()
      throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    respondingPeer.respondTimes(responder, 20);
    future.whenComplete(
        (response, error) -> {
          done.compareAndSet(false, true);
        });
    assertThat(future.isDone()).isFalse();
  }
}
