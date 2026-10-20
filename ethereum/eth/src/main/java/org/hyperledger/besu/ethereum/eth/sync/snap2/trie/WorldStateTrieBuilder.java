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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.Accounts;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeManager;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.patricia.AscendingCollapsePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.AscendingCollapsePatriciaTrie.Subtree;
import org.hyperledger.besu.ethereum.trie.patricia.AscendingCollapsePatriciaTrie.SubtreeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the account trie and the storage tries from the flat state, in parallel over the 256
 * first-byte partitions of the account key space, and fixes the storage roots of the flat accounts.
 * The storage tries the download wrote are kept without reading them: a storage with a known root
 * has its trie unless the download left it, and a storage a move changed has an unknown root until
 * its trie is updated.
 */
public final class WorldStateTrieBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(WorldStateTrieBuilder.class);
  private static final int PARTITIONS = 256;
  private static final int ACCOUNTS_PER_COMMIT = 10_000;
  private static final int SLOTS_PER_CANCEL_CHECK = 65_536;

  private final SyncStateStorage storage;
  private final Predicate<Bytes32> trieLeft;
  private final Supplier<ExecutorService> executors;
  private final AtomicLong accounts = new AtomicLong();
  private final AtomicLong slots = new AtomicLong();
  private final AtomicLong keptStorageTries = new AtomicLong();
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

  /**
   * Creates a build.
   *
   * @param trieLeft whether the download completed the storage of an account without its trie
   */
  public WorldStateTrieBuilder(
      final SyncStateStorage storage, final int threads, final Predicate<Bytes32> trieLeft) {
    this(
        storage,
        trieLeft,
        () ->
            Executors.newFixedThreadPool(
                Math.max(1, threads),
                Thread.ofPlatform().name("snap2-trie-builder-", 0).daemon().factory()));
  }

  /** Creates a build whose partitions run on the executor {@code executors} gives. */
  @VisibleForTesting
  WorldStateTrieBuilder(
      final SyncStateStorage storage,
      final Predicate<Bytes32> trieLeft,
      final Supplier<ExecutorService> executors) {
    this.storage = storage;
    this.trieLeft = trieLeft;
    this.executors = executors;
  }

  /**
   * Result of a build.
   *
   * @param root the state root
   * @param rootNode the encoding of the root node
   * @param accounts the number of accounts
   * @param slots the number of storage slots read to build tries
   * @param keptStorageTries the number of storage tries built while downloading and kept
   */
  public record Result(
      Bytes32 root, Bytes rootNode, long accounts, long slots, long keptStorageTries) {}

  /** Returns the number of accounts processed so far. */
  public long accountsProcessed() {
    return accounts.get();
  }

  /** Returns the number of slots processed so far. */
  public long slotsProcessed() {
    return slots.get();
  }

  /** Returns the number of storage tries built while downloading and kept so far. */
  public long keptStorageTries() {
    return keptStorageTries.get();
  }

  /** Stops a running build, which then throws a {@link CancellationException}. */
  public void cancel() {
    cancelled.set(true);
  }

  /** Builds the tries and writes their nodes. */
  public Result build() {
    final ExecutorService executor = executors.get();
    try {
      final SyncStateStorage.TrieWriter writer = storage.trieWriter();
      final Bytes[] rootNode = {MerkleTrie.EMPTY_TRIE_NODE};
      final AscendingCollapsePatriciaTrie top =
          new AscendingCollapsePatriciaTrie(
              (location, hash, rlp) -> {
                writer.accountNode(location, hash, rlp);
                if (location.isEmpty()) {
                  rootNode[0] = rlp;
                }
              });
      final List<Future<Optional<Subtree>>> parts = new ArrayList<>(PARTITIONS);
      for (int prefix = 0; prefix < PARTITIONS; prefix++) {
        final int p = prefix;
        parts.add(executor.submit(() -> buildPartition(top, p)));
      }
      for (final Future<Optional<Subtree>> part : parts) {
        part.get().ifPresent(top::insert);
      }
      final Bytes32 root = top.rootHash();
      writer.commit();
      return new Result(root, rootNode[0], accounts.get(), slots.get(), keptStorageTries.get());
    } catch (final InterruptedException e) {
      cancel();
      Thread.currentThread().interrupt();
      throw new CancellationException("trie build interrupted");
    } catch (final ExecutionException e) {
      cancel();
      final RuntimeException first = failure.get();
      throw first != null ? first : new IllegalStateException("trie build failed", e.getCause());
    } finally {
      executor.shutdownNow();
      awaitPartitions(executor);
    }
  }

  // no partition writes once the build returned
  private static void awaitPartitions(final ExecutorService executor) {
    try {
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        LOG.warn("Trie build partitions still running a minute after the build stopped");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // the accounts of one first byte, as a subtree of the account trie; none if it has no account
  private Optional<Subtree> buildPartition(
      final AscendingCollapsePatriciaTrie top, final int prefix) {
    final byte[] from = new byte[32];
    final byte[] to = RangeManager.MAX_RANGE.toArray();
    from[0] = (byte) prefix;
    to[0] = (byte) prefix;
    final SyncStateStorage.TrieWriter writer = storage.trieWriter();
    try {
      final PartitionVisitor visitor =
          new PartitionVisitor(writer, top.subtreeBuilder(writer::accountNode));
      storage.scan(Bytes32.wrap(from), Bytes32.wrap(to), visitor);
      visitor.finishAccount();
      final Optional<Subtree> subtree =
          visitor.accountTrie.isEmpty()
              ? Optional.empty()
              : Optional.of(visitor.accountTrie.build());
      visitor.commitFixes();
      writer.commit();
      return subtree;
    } catch (final RuntimeException e) {
      // the other partitions stop too, and the build reports the first failure
      failure.compareAndSet(null, e);
      cancel();
      writer.abort();
      throw e;
    }
  }

  private final class PartitionVisitor implements SyncStateStorage.ScanVisitor {
    private final SyncStateStorage.TrieWriter writer;
    private final SubtreeBuilder accountTrie;
    private SyncStateStorage.Batch fixes;
    private int pendingFixes;
    private Bytes32 accountHash;
    private Bytes account;
    private AscendingCollapsePatriciaTrie storageTrie;
    private long partitionSlots;

    private PartitionVisitor(
        final SyncStateStorage.TrieWriter writer, final SubtreeBuilder accountTrie) {
      this.writer = writer;
      this.accountTrie = accountTrie;
    }

    @Override
    public boolean onAccount(final Bytes32 hash, final Bytes rlp) {
      finishAccount();
      if (cancelled.get()) {
        throw new CancellationException("trie build cancelled");
      }
      final Hash storageRoot = Accounts.decode(rlp).getStorageRoot();
      if (Accounts.hasBuildableStorage(storageRoot) && !trieLeft.test(hash)) {
        accountTrie.insert(hash, rlp);
        accounts.incrementAndGet();
        keptStorageTries.incrementAndGet();
        return false;
      }
      accountHash = hash;
      account = rlp;
      storageTrie =
          new AscendingCollapsePatriciaTrie(
              (location, nodeHash, node) -> writer.storageNode(hash, location, nodeHash, node));
      return true;
    }

    @Override
    public void onSlot(final Bytes32 slotAccount, final Bytes32 slotHash, final Bytes value) {
      if (!slotAccount.equals(accountHash)) {
        LOG.debug("Dropping slot {} of missing account {}", slotHash, slotAccount);
        fixes().removeStorage(slotAccount, slotHash);
        return;
      }
      if (++partitionSlots % SLOTS_PER_CANCEL_CHECK == 0 && cancelled.get()) {
        throw new CancellationException("trie build cancelled");
      }
      storageTrie.insert(slotHash, RLP.encodeOne(value.trimLeadingZeros()));
      slots.incrementAndGet();
    }

    private void finishAccount() {
      if (accountHash == null) {
        return;
      }
      final Bytes32 storageRoot = storageTrie.rootHash();
      final PatriciaTrieAccountValue value = Accounts.decode(account);
      Bytes leaf = account;
      if (!value.getStorageRoot().getBytes().equals(storageRoot)) {
        leaf = Accounts.encode(Accounts.withStorageRoot(value, Hash.wrap(storageRoot)));
        fixes().putAccount(accountHash, leaf);
      }
      accountTrie.insert(accountHash, leaf);
      accounts.incrementAndGet();
      accountHash = null;
      storageTrie = null;
    }

    private SyncStateStorage.Batch fixes() {
      if (fixes == null) {
        fixes = storage.batch();
      }
      if (++pendingFixes >= ACCOUNTS_PER_COMMIT) {
        commitFixes();
        fixes = storage.batch();
      }
      return fixes;
    }

    private void commitFixes() {
      if (fixes != null) {
        fixes.commit();
        fixes = null;
        pendingFixes = 0;
      }
    }
  }
}
