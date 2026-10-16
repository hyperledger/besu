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
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.Accounts;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Format;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Storages;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.World;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorldStateTrieBuilderTest {

  @ParameterizedTest
  @EnumSource(Format.class)
  void buildsTheStateTrieFromTheFlatState(final Format format) {
    final Storages storages = SyncStorageFixture.create(format);
    final World world = SyncStorageFixture.randomWorld(new Random(1), 2000);
    world.writeFlat(storages.sync());

    final WorldStateTrieBuilder.Result result =
        new WorldStateTrieBuilder(storages.sync(), 4, hash -> false).build();

    assertThat(result.root()).isEqualTo(world.root());
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
    new WorldStateTrieBuilder(storages.sync(), 2, hash -> false).build();
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

    final WorldStateTrieBuilder.Result result =
        new WorldStateTrieBuilder(storages.sync(), 2, hash -> false).build();

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
    new WorldStateTrieBuilder(storages.sync(), 2, hash -> false).build();

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

    final WorldStateTrieBuilder.Result result =
        new WorldStateTrieBuilder(storages.sync(), 2, left::equals).build();

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

      final WorldStateTrieBuilder.Result result =
          new WorldStateTrieBuilder(storages.sync(), 2, hash -> false).build();

      assertThat(result.root()).isEqualTo(world.root());
    }
  }

  @Test
  void stopsEveryPartitionWhenOneFails() throws InterruptedException {
    final Storages storages = SyncStorageFixture.create(Format.BONSAI);
    SyncStorageFixture.randomWorld(new Random(3), 2000).writeFlat(storages.sync());
    final SyncStateStorage storage = spy(storages.sync());
    final IllegalStateException diskFailure = new IllegalStateException("disk failure");
    final WorldStateTrieBuilder builder = new WorldStateTrieBuilder(storage, 4, hash -> false);
    final CountDownLatch failed = new CountDownLatch(1);
    final CountDownLatch secondEnded = new CountDownLatch(1);
    final AtomicBoolean secondCompleted = new AtomicBoolean();
    doAnswer(
            invocation -> {
              final Bytes32 from = invocation.getArgument(0);
              if (from.get(0) == 0) {
                failed.countDown();
                throw diskFailure;
              }
              if (from.get(0) != 1) {
                return invocation.callRealMethod();
              }
              try {
                // a database scan ignores interrupts, and this one starts after the failure
                Uninterruptibles.awaitUninterruptibly(failed);
                awaitCancellation(builder);
                invocation.callRealMethod();
                secondCompleted.set(true);
                return null;
              } finally {
                secondEnded.countDown();
              }
            })
        .when(storage)
        .scan(any(), any(), any());

    assertThatThrownBy(builder::build).isSameAs(diskFailure);
    assertThat(secondEnded.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(secondCompleted).isFalse();
  }

  // the failing partition cancels the build right after failing; long enough for a missed
  // cancellation to let the other partition complete
  private static void awaitCancellation(final WorldStateTrieBuilder builder) {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!builder.isCancelled() && System.nanoTime() < deadline) {
      Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
    }
  }
}
