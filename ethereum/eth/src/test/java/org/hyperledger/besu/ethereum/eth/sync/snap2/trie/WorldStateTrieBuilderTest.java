/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.snap2.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.Accounts;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Format;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Storages;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.World;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorldStateTrieBuilderTest {

  private static final int ACCOUNTS_PER_COMMIT = 3;

  @ParameterizedTest
  @EnumSource(Format.class)
  void buildsTheStateTrieFromTheFlatState(final Format format) {
    final Storages storages = SyncStorageFixture.create(format);
    final World world = SyncStorageFixture.randomWorld(new Random(1), 2000);
    world.writeFlat(storages.sync());

    final WorldStateTrieBuilder.Result result = builder(storages.sync(), hash -> false).build();

    assertThat(result.root()).isEqualTo(world.root());
    assertThat(Hash.hash(result.rootNode()).getBytes()).isEqualTo(result.root());
    assertThat(result.accounts()).isEqualTo(world.accounts.size());
    assertThat(result.slots())
        .isEqualTo(world.storage.values().stream().mapToLong(slots -> slots.size()).sum());

    // the written nodes can be read back by the storage format
    final MerkleTrie<Bytes32, Bytes> accountTrie = storages.accountTrie(result.root());
    world.accountLeaves().forEach((hash, leaf) -> assertThat(accountTrie.get(hash)).contains(leaf));
    world.storage.keySet().stream()
        .limit(100)
        .forEach(
            account -> {
              final MerkleTrie<Bytes32, Bytes> storageTrie =
                  storages.storageTrie(account, world.storageRoot(account));
              world
                  .storageLeaves(account)
                  .forEach((slot, leaf) -> assertThat(storageTrie.get(slot)).contains(leaf));
            });

    // the flat accounts now carry the right storage roots
    world.accounts.keySet().stream()
        .limit(200)
        .forEach(
            hash -> {
              final PatriciaTrieAccountValue flat =
                  Accounts.decode(storages.sync().getAccount(hash).orElseThrow());
              assertThat(flat.getStorageRoot().getBytes()).isEqualTo(world.storageRoot(hash));
            });
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void keepsTheStorageTriesWrittenAndRebuildsTheChangedOnes(final Format format) {
    final Storages storages = SyncStorageFixture.create(format);
    final World world = SyncStorageFixture.randomWorld(new Random(7), 500);
    world.writeFlat(storages.sync());
    builder(storages.sync(), hash -> false).build();
    final long withStorage =
        world.storage.values().stream().filter(slots -> !slots.isEmpty()).count();

    // a slot changes after its trie was written, as in a pivot move
    final Bytes32 changed =
        world.storage.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .findFirst()
            .orElseThrow()
            .getKey();
    final Bytes32 slot = world.storage.get(changed).firstKey();
    world.storage.get(changed).put(slot, UInt256.valueOf(424242));
    final SyncStateStorage.Batch batch = storages.sync().batch();
    batch.putStorage(changed, slot, UInt256.valueOf(424242));
    batch.putAccount(
        changed,
        Accounts.encode(
            Accounts.withStorageRoot(
                Accounts.decode(storages.sync().getAccount(changed).orElseThrow()),
                Accounts.UNKNOWN_STORAGE_ROOT)));
    batch.commit();

    final WorldStateTrieBuilder.Result result = builder(storages.sync(), hash -> false).build();

    assertThat(result.root()).isEqualTo(world.root());
    assertThat(result.keptStorageTries()).isEqualTo(withStorage - 1);
    assertThat(result.slots()).isEqualTo(world.storage.get(changed).size());
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void rebuildsTheStorageTriesTheDownloadLeft(final Format format) {
    final Storages storages = SyncStorageFixture.create(format);
    final World world = SyncStorageFixture.randomWorld(new Random(9), 500);
    world.writeFlat(storages.sync());
    builder(storages.sync(), hash -> false).build();

    // a slot of a storage the download completed without its trie, its root still known
    final Bytes32 left =
        world.storage.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .findFirst()
            .orElseThrow()
            .getKey();
    final Bytes32 slot = world.storage.get(left).firstKey();
    world.storage.get(left).put(slot, UInt256.valueOf(424242));
    final SyncStateStorage.Batch batch = storages.sync().batch();
    batch.putStorage(left, slot, UInt256.valueOf(424242));
    batch.commit();

    final WorldStateTrieBuilder.Result result = builder(storages.sync(), left::equals).build();

    assertThat(result.root()).isEqualTo(world.root());
    assertThat(result.slots()).isEqualTo(world.storage.get(left).size());
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void buildsSmallStates(final Format format) {
    for (int size : new int[] {0, 1, 2, 5}) {
      final Storages storages = SyncStorageFixture.create(format);
      final World world = SyncStorageFixture.randomWorld(new Random(size), size);
      world.writeFlat(storages.sync());

      final WorldStateTrieBuilder.Result result = builder(storages.sync(), hash -> false).build();

      assertThat(result.root()).isEqualTo(world.root());
      assertThat(Hash.hash(result.rootNode()).getBytes()).isEqualTo(result.root());
    }
  }

  @Test
  void commitsTheFixesInFullBatches() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(5), 2000);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());

    final WorldStateTrieBuilder.Result result = builder(storage, hash -> false).build();

    assertThat(result.root()).isEqualTo(world.root());
    // every flat root is stale, so each partition fixes all its accounts
    final long batches =
        world.accounts.keySet().stream()
            .collect(Collectors.groupingBy(hash -> hash.get(0), Collectors.counting()))
            .values()
            .stream()
            .mapToLong(fixes -> (fixes + ACCOUNTS_PER_COMMIT - 1) / ACCOUNTS_PER_COMMIT)
            .sum();
    verify(storage, times((int) batches)).batch();
    for (final Bytes32 hash : world.accounts.keySet()) {
      final PatriciaTrieAccountValue flat = Accounts.decode(storage.getAccount(hash).orElseThrow());
      assertThat(flat.getStorageRoot().getBytes()).isEqualTo(world.storageRoot(hash));
    }
  }

  @Test
  void commitsTheStorageNodesBeforeTheRootsThatPointToThem() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(8), 2000);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    final List<Bytes32> checked = new ArrayList<>();
    doAnswer(
            invocation -> {
              final SyncStateStorage.Batch batch = spy(storages.sync().batch());
              final List<Bytes32> fixed = new ArrayList<>();
              doAnswer(
                      put -> {
                        fixed.add(put.getArgument(0));
                        put.callRealMethod();
                        return null;
                      })
                  .when(batch)
                  .putAccount(any(), any());
              // a crash right after this commit keeps these roots: their tries must be stored
              doAnswer(
                      commit -> {
                        commit.callRealMethod();
                        fixed.stream()
                            .filter(world.storage::containsKey)
                            .forEach(
                                account -> {
                                  final MerkleTrie<Bytes32, Bytes> trie =
                                      storages.storageTrie(account, world.storageRoot(account));
                                  world
                                      .storageLeaves(account)
                                      .forEach(
                                          (slot, leaf) ->
                                              assertThat(trie.get(slot)).contains(leaf));
                                  checked.add(account);
                                });
                        return null;
                      })
                  .when(batch)
                  .commit();
              return batch;
            })
        .when(storage)
        .batch();

    assertThat(builder(storage, hash -> false).build().root()).isEqualTo(world.root());
    assertThat(checked).hasSize(world.storage.size());
  }

  @Test
  void throwsWhenCancelledOnceThePartitionsAreDone() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(4), 100);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    final WorldStateTrieBuilder builder = builder(storage, hash -> false);
    // cancelled once the last partition read its accounts, past its last check
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              final Bytes32 from = invocation.getArgument(0);
              if ((from.get(0) & 0xff) == 255) {
                builder.cancel();
              }
              return null;
            })
        .when(storage)
        .scan(any(), any(), any());

    assertThatThrownBy(builder::build).isInstanceOf(CancellationException.class);
  }

  @Test
  void stopsEveryPartitionWhenOneFails() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(3), 2000);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    final IllegalStateException diskFailure = new IllegalStateException("disk failure");
    final Set<Integer> completed = new HashSet<>();
    doAnswer(
            invocation -> {
              final Bytes32 from = invocation.getArgument(0);
              final int prefix = from.get(0) & 0xff;
              if (prefix == 0) {
                throw diskFailure;
              }
              invocation.callRealMethod();
              completed.add(prefix);
              return null;
            })
        .when(storage)
        .scan(any(), any(), any());

    assertThatThrownBy(() -> builder(storage, hash -> false).build()).isSameAs(diskFailure);
    // the partitions after the failed one stop at their first account
    final Set<Integer> withAccounts =
        world.accounts.keySet().stream()
            .map(hash -> hash.get(0) & 0xff)
            .collect(Collectors.toSet());
    assertThat(completed).doesNotContainAnyElementsOf(withAccounts);
  }

  @Test
  void stopsThePartitionsWhenTheBuildFailsOnItsOwnThread() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(6), 2000);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    // the writer of the top of the account trie, created first, fails on its first node
    final IllegalStateException diskFailure = new IllegalStateException("disk failure");
    final SyncStateStorage.TrieWriter topWriter = mock(SyncStateStorage.TrieWriter.class);
    doThrow(diskFailure).when(topWriter).accountNode(any(), any(), any());
    doReturn(topWriter).doCallRealMethod().when(storage).trieWriter();
    final OnDemandExecutor partitions = new OnDemandExecutor();

    assertThatThrownBy(
            () ->
                new WorldStateTrieBuilder(
                        storage, hash -> false, () -> partitions, ACCOUNTS_PER_COMMIT)
                    .build())
        .isSameAs(diskFailure);
    // the partitions the build did not wait for run now, as on other threads, and write nothing
    final long ran = partitions.tasks.stream().filter(FutureTask::isDone).count();
    assertThat(ran).isBetween(1L, 255L);
    partitions.tasks.forEach(FutureTask::run);
    world.accounts.keySet().stream()
        .filter(hash -> (hash.get(0) & 0xff) >= ran)
        .forEach(
            hash ->
                assertThat(Accounts.decode(storage.getAccount(hash).orElseThrow()).getStorageRoot())
                    .isEqualTo(Accounts.UNKNOWN_STORAGE_ROOT));
  }

  @Test
  void abortsTheFixesOfAFailedPartition() {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    final World world = SyncStorageFixture.randomWorld(new Random(3), 2000);
    world.writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    final List<SyncStateStorage.Batch> batches = new ArrayList<>();
    doAnswer(
            invocation -> {
              final SyncStateStorage.Batch batch = spy(storages.sync().batch());
              batches.add(batch);
              return batch;
            })
        .when(storage)
        .batch();
    // the first partition fails once it read its accounts, its last fixes not committed
    final IllegalStateException diskFailure = new IllegalStateException("disk failure");
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              final Bytes32 from = invocation.getArgument(0);
              if (from.get(0) == 0) {
                throw diskFailure;
              }
              return null;
            })
        .when(storage)
        .scan(any(), any(), any());

    assertThatThrownBy(() -> builder(storage, hash -> false).build()).isSameAs(diskFailure);
    // the other partitions stop before their first fix: the last batch is the failed one's
    assertThat(batches).isNotEmpty();
    verify(batches.getLast(), never()).commit();
    verify(batches.getLast()).abort();
  }

  // the partitions run one after the other on the test thread, with small batches of fixes
  private static WorldStateTrieBuilder builder(
      final SyncStateStorage storage, final Predicate<Bytes32> trieLeft) {
    return new WorldStateTrieBuilder(
        storage, trieLeft, MoreExecutors::newDirectExecutorService, ACCOUNTS_PER_COMMIT);
  }

  // runs each task on the test thread once the build waits for it, the others when the test says
  private static final class OnDemandExecutor extends AbstractExecutorService {
    private final List<FutureTask<?>> tasks = new ArrayList<>();

    @Override
    protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
      final FutureTask<T> task =
          new FutureTask<>(callable) {
            @Override
            public T get() throws InterruptedException, ExecutionException {
              run();
              return super.get();
            }
          };
      tasks.add(task);
      return task;
    }

    @Override
    public void execute(final Runnable command) {}

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return true;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return true;
    }
  }
}
