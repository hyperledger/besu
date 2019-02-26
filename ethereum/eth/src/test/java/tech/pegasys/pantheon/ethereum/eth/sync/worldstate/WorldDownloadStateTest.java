/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.pantheon.ethereum.eth.sync.worldstate.NodeDataRequest.createAccountDataRequest;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.queue.InMemoryTaskQueue;
import tech.pegasys.pantheon.services.queue.TaskQueue.Task;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

public class WorldDownloadStateTest {

  private static final BytesValue ROOT_NODE_DATA = BytesValue.of(1, 2, 3, 4);
  private static final Hash ROOT_NODE_HASH = Hash.hash(ROOT_NODE_DATA);
  private static final int MAX_OUTSTANDING_REQUESTS = 3;

  private final WorldStateStorage worldStateStorage =
      new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage());

  private final BlockHeader header =
      new BlockHeaderTestFixture().stateRoot(ROOT_NODE_HASH).buildHeader();
  private final InMemoryTaskQueue<NodeDataRequest> pendingRequests = new InMemoryTaskQueue<>();
  private final ArrayBlockingQueue<Task<NodeDataRequest>> requestsToPersist =
      new ArrayBlockingQueue<>(100);

  private final WorldDownloadState downloadState =
      new WorldDownloadState(pendingRequests, requestsToPersist, MAX_OUTSTANDING_REQUESTS);

  private final CompletableFuture<Void> future = downloadState.getDownloadFuture();

  @Before
  public void setUp() {
    downloadState.setRootNodeData(ROOT_NODE_DATA);
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldCompleteReturnedFutureWhenNoPendingTasksRemain() {
    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isCompleted();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldStoreRootNodeBeforeReturnedFutureCompletes() {
    final CompletableFuture<Void> postFutureChecks =
        future.thenAccept(
            result ->
                assertThat(worldStateStorage.getAccountStateTrieNode(ROOT_NODE_HASH))
                    .contains(ROOT_NODE_DATA));

    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isCompleted();
    assertThat(postFutureChecks).isCompleted();
  }

  @Test
  public void shouldNotCompleteWhenThereArePendingTasks() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldCancelOutstandingTasksWhenFutureIsCancelled() {
    final EthTask<?> persistenceTask = mock(EthTask.class);
    final EthTask<?> outstandingTask1 = mock(EthTask.class);
    final EthTask<?> outstandingTask2 = mock(EthTask.class);
    final Task<NodeDataRequest> toPersist1 = mock(Task.class);
    final Task<NodeDataRequest> toPersist2 = mock(Task.class);
    downloadState.setPersistenceTask(persistenceTask);
    downloadState.addOutstandingTask(outstandingTask1);
    downloadState.addOutstandingTask(outstandingTask2);

    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY));
    requestsToPersist.add(toPersist1);
    requestsToPersist.add(toPersist2);

    future.cancel(true);

    verify(persistenceTask).cancel();
    verify(outstandingTask1).cancel();
    verify(outstandingTask2).cancel();

    assertThat(pendingRequests.isEmpty()).isTrue();
    assertThat(requestsToPersist).isEmpty();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldNotSendAdditionalRequestsWhenWaitingForANewPeer() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    downloadState.setWaitingForNewPeer(true);
    downloadState.whileAdditionalRequestsCanBeSent(mustNotBeCalled());
  }

  @Test
  public void shouldResumeSendingAdditionalRequestsWhenNoLongerWaitingForPeer() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    final Runnable sendRequest =
        mockWithAction(() -> downloadState.addOutstandingTask(mock(EthTask.class)));

    downloadState.setWaitingForNewPeer(true);
    downloadState.whileAdditionalRequestsCanBeSent(mustNotBeCalled());

    downloadState.setWaitingForNewPeer(false);
    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);
    verify(sendRequest, times(MAX_OUTSTANDING_REQUESTS)).run();
  }

  @Test
  public void shouldStopSendingAdditionalRequestsWhenPendingRequestsIsEmpty() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    final Runnable sendRequest = mockWithAction(pendingRequests::dequeue);
    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);

    verify(sendRequest, times(2)).run();
  }

  @Test
  public void shouldStopSendingAdditionalRequestsWhenMaximumOutstandingRequestCountReached() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    final Runnable sendRequest =
        mockWithAction(() -> downloadState.addOutstandingTask(mock(EthTask.class)));

    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);
    verify(sendRequest, times(MAX_OUTSTANDING_REQUESTS)).run();
  }

  @Test
  public void shouldStopSendingAdditionalRequestsWhenFutureIsCancelled() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    final Runnable sendRequest = mockWithAction(() -> future.cancel(true));

    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);
    verify(sendRequest, times(1)).run();
  }

  @Test
  public void shouldStopSendingAdditionalRequestsWhenDownloadIsMarkedAsStalled() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    final Runnable sendRequest = mockWithAction(() -> downloadState.markAsStalled(1));

    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);
    verify(sendRequest, times(1)).run();
  }

  @Test
  public void shouldNotAllowMultipleCallsToSendAdditionalRequestsAtOnce() {
    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    final Runnable sendRequest =
        mockWithAction(
            () -> {
              downloadState.whileAdditionalRequestsCanBeSent(mustNotBeCalled());
              downloadState.addOutstandingTask(mock(EthTask.class));
            });

    downloadState.whileAdditionalRequestsCanBeSent(sendRequest);
    verify(sendRequest, times(MAX_OUTSTANDING_REQUESTS)).run();
  }

  @Test
  public void shouldNotEnqueueRequestsAfterDownloadIsStalled() {
    downloadState.checkCompletion(worldStateStorage, header);

    downloadState.enqueueRequests(Stream.of(createAccountDataRequest(Hash.EMPTY_TRIE_HASH)));
    downloadState.enqueueRequest(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    assertThat(pendingRequests.isEmpty()).isTrue();
  }

  @Test // Sanity check for the test structure
  public void shouldFailWhenMustNotBeCalledIsCalled() {

    pendingRequests.enqueue(createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    assertThatThrownBy(() -> downloadState.whileAdditionalRequestsCanBeSent(mustNotBeCalled()))
        .hasMessage("Unexpected invocation");
  }

  private Runnable mustNotBeCalled() {
    return () -> fail("Unexpected invocation");
  }

  private Runnable mockWithAction(final Runnable action) {
    final Runnable runnable = mock(Runnable.class);
    doAnswer(
            invocation -> {
              action.run();
              return null;
            })
        .when(runnable)
        .run();
    return runnable;
  }
}
