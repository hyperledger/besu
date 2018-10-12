package tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractMessageTaskTest<T, R> {
  protected static Blockchain blockchain;
  protected static ProtocolSchedule<Void> protocolSchedule;
  protected static ProtocolContext<Void> protocolContext;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected AtomicBoolean peersDoTimeout;
  protected AtomicInteger peerCountToTimeout;

  @BeforeClass
  public static void setup() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    assert (blockchainSetupUtil.getMaxBlockNumber() >= 20L);
  }

  @Before
  public void setupTest() {
    peersDoTimeout = new AtomicBoolean(false);
    peerCountToTimeout = new AtomicInteger(0);
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain, () -> peerCountToTimeout.getAndDecrement() > 0 || peersDoTimeout.get());
    ethContext = ethProtocolManager.ethContext();
  }

  protected abstract T generateDataToBeRequested();

  protected abstract EthTask<R> createTask(T requestedData);

  protected abstract void assertResultMatchesExpectation(
      T requestedData, R response, EthPeer respondingPeer);

  @Test
  public void completesWhenPeersAreResponsive() throws ExecutionException, InterruptedException {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested and expected response
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicReference<R> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    respondingPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(requestedData, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void doesNotCompleteWhenPeersDoNotRespond()
      throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    future.whenComplete(
        (response, error) -> {
          done.compareAndSet(false, true);
        });
    assertThat(done).isFalse();
  }

  @Test
  public void cancel() throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();

    assertThat(future.isDone()).isFalse();
    task.cancel();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();
    assertThat(task.run().isCancelled()).isTrue();
  }
}
