/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache;

import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;
import static org.hyperledger.besu.metrics.BesuMetricCategory.BLOCKCHAIN;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.PreloadTask;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.Preloader;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.StoragePreloadRequest;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.StorageSubscriber;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiCachedMerkleTrieLoader implements StorageSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiCachedMerkleTrieLoader.class);

  private static final int ACCOUNT_CACHE_SIZE = 100_000;
  private static final int STORAGE_CACHE_SIZE = 200_000;
  private static final int STRIDE = 4;

  private final Cache<Bytes, Bytes> accountNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(ACCOUNT_CACHE_SIZE).build();
  private final Cache<Bytes, Bytes> storageNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(STORAGE_CACHE_SIZE).build();

  private final OperationTimer accountPreloadTimer;
  private final OperationTimer storagePreloadTimer;
  private final OperationTimer processPreloadTimer;

  private final Counter accountCacheMissCounter;
  private final Counter storageCacheMissCounter;

  private final Preloader preloader;

  public BonsaiCachedMerkleTrieLoader(
      final ObservableMetricsSystem metricsSystem, final Preloader preloader) {
    // LOG.info("Creating BonsaiCachedMerkleTrieLoader " + this);
    // Thread.dumpStack();
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "accountsNodes", accountNodes);
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "storageNodes", storageNodes);

    this.preloader = preloader;

    accountPreloadTimer =
        metricsSystem
            .createLabelledTimer(
                BLOCKCHAIN,
                "account_preload_latency_seconds",
                "Latency for preloading account nodes",
                "database")
            .labels("besu");
    storagePreloadTimer =
        metricsSystem
            .createLabelledTimer(
                BLOCKCHAIN,
                "storage_preload_latency_seconds",
                "Latency for preloading storage nodes",
                "database")
            .labels("besu");
    processPreloadTimer =
        metricsSystem
            .createLabelledTimer(
                BLOCKCHAIN,
                "preload_task_latency_seconds",
                "Latency for processing the preload task",
                "database")
            .labels("besu");

    accountCacheMissCounter =
        metricsSystem.createCounter(
            BLOCKCHAIN, "account_cache_miss_count", "Counter for account state trie cache misses");
    storageCacheMissCounter =
        metricsSystem.createCounter(
            BLOCKCHAIN,
            "storage_cache_miss_count",
            "Counter for account storage trie cache misses");
  }

  public void preLoadAccount(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash worldStateRootHash,
      final Address account) {
    CompletableFuture.runAsync(
        () -> cacheAccountNodes(worldStateKeyValueStorage, worldStateRootHash, account));
  }

  @VisibleForTesting
  public void cacheAccountNodes(
      final BonsaiWorldStateKeyValueStorage storage,
      final Hash worldStateRootHash,
      final Address account) {
    LOG.error("cacheAccountNodes >> should not get here");
    final OperationTimer.TimingContext timer = accountPreloadTimer.startTimer();
    final long subscriptionId = storage.subscribe(this);
    try {
      Bytes path = bytesToPath(account.addressHash());
      int size = path.size();

      List<Integer> markers = new ArrayList<>();
      for (int idx = 0; idx < size; idx += STRIDE) {
        markers.add(idx);
      }
      if (markers.get(markers.size() - 1) != size - 1) {
        markers.add(size - 1);
      }

      List<byte[]> markerKeys =
          markers.stream().map(i -> path.slice(0, i).toArrayUnsafe()).collect(Collectors.toList());
      List<byte[]> markerValues = storage.getMultipleKeys(markerKeys);

      Set<Integer> liveMarkers = new HashSet<>();
      for (int k = 0; k < markers.size(); k++) {
        byte[] raw = markerValues.get(k);
        if (raw != null) {
          Bytes node = Bytes.wrap(raw);
          accountNodes.put(Hash.hash(node), node);
          liveMarkers.add(markers.get(k));
        }
      }

      List<byte[]> secondKeys = new ArrayList<>();
      for (int m = 0; m < markers.size() - 1; m++) {
        int start = markers.get(m);
        int end = markers.get(m + 1);
        if (!liveMarkers.contains(start) || end - start <= 1) {
          continue;
        }
        for (int i = start + 1; i < end; i++) {
          secondKeys.add(path.slice(0, i).toArrayUnsafe());
        }
      }

      if (!secondKeys.isEmpty()) {
        List<byte[]> secondValues = storage.getMultipleKeys(secondKeys);
        for (byte[] raw : secondValues) {
          if (raw != null) {
            Bytes node = Bytes.wrap(raw);
            accountNodes.put(Hash.hash(node), node);
          }
        }
      }
    } catch (Exception ex) {
      LOG.error("Error caching account nodes", ex);
    } finally {
      storage.unSubscribe(subscriptionId);
      timer.close();
    }
  }

  public void preLoadStorageSlot(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Address account,
      final StorageSlotKey slotKey) {
    CompletableFuture.runAsync(
        () -> cacheStorageNodes(worldStateKeyValueStorage, account, slotKey));
  }

  @VisibleForTesting
  public void cacheStorageNodes(
      final BonsaiWorldStateKeyValueStorage storage,
      final Address account,
      final StorageSlotKey slotKey) {
    LOG.error("cacheStorageNodes >> should not get here");

    final OperationTimer.TimingContext timer = storagePreloadTimer.startTimer();
    final long subscriptionId = storage.subscribe(this);
    try {
      Bytes path = bytesToPath(slotKey.getSlotHash());
      Bytes accountHash = account.addressHash();
      int size = path.size();

      List<Integer> markers = new ArrayList<>();
      for (int idx = 0; idx < size; idx += STRIDE) {
        markers.add(idx);
      }
      if (markers.get(markers.size() - 1) != size - 1) {
        markers.add(size - 1);
      }

      List<byte[]> markerKeys =
          markers.stream()
              .map(i -> Bytes.concatenate(accountHash, path.slice(0, i)).toArrayUnsafe())
              .collect(Collectors.toList());
      List<byte[]> markerValues = storage.getMultipleKeys(markerKeys);

      Set<Integer> liveMarkers = new HashSet<>();
      for (int k = 0; k < markers.size(); k++) {
        byte[] raw = markerValues.get(k);
        if (raw != null) {
          Bytes node = Bytes.wrap(raw);
          storageNodes.put(Hash.hash(node), node);
          liveMarkers.add(markers.get(k));
        }
      }

      List<byte[]> secondKeys = new ArrayList<>();
      for (int m = 0; m < markers.size() - 1; m++) {
        int start = markers.get(m);
        int end = markers.get(m + 1);
        if (!liveMarkers.contains(start) || end - start <= 1) {
          continue;
        }
        for (int i = start + 1; i < end; i++) {
          secondKeys.add(Bytes.concatenate(accountHash, path.slice(0, i)).toArrayUnsafe());
        }
      }

      if (!secondKeys.isEmpty()) {
        List<byte[]> secondValues = storage.getMultipleKeys(secondKeys);
        for (byte[] raw : secondValues) {
          if (raw != null) {
            Bytes node = Bytes.wrap(raw);
            storageNodes.put(Hash.hash(node), node);
          }
        }
      }
    } catch (Exception ex) {
      LOG.error("Error caching storage nodes", ex);
    } finally {
      storage.unSubscribe(subscriptionId);
      timer.close();
    }
  }

  public void processPreloadTask(final PreloadTask task) {
    BonsaiWorldStateKeyValueStorage storage =
        (BonsaiWorldStateKeyValueStorage) task.getWorldStateKeyValueStorage();
    final OperationTimer.TimingContext timer = processPreloadTimer.startTimer();
    final long subscriptionId = storage.subscribe(this);
    try {
      List<byte[]> firstPassKeys = new ArrayList<>();
      List<List<Bytes>> allPaths = new ArrayList<>();
      List<Boolean> allIsAccountList = new ArrayList<>();
      List<List<Integer>> allMarkerIndicesList = new ArrayList<>();

      for (Address account : task.getAccountPreloads()) {
        Bytes path = bytesToPath(account.addressHash());
        List<Bytes> slices = new ArrayList<>();
        for (int i = 1; i <= path.size(); i++) slices.add(path.slice(0, i));
        allPaths.add(slices);
        allIsAccountList.add(true);

        List<Integer> markers = new ArrayList<>();
        for (int i = 0; i < slices.size(); i += STRIDE) markers.add(i);
        if (markers.get(markers.size() - 1) != slices.size() - 1) {
          markers.add(slices.size() - 1);
        }
        allMarkerIndicesList.add(markers);
        for (int i : markers) firstPassKeys.add(slices.get(i).toArrayUnsafe());
      }

      for (StoragePreloadRequest req : task.getStoragePreloads()) {
        Bytes accountHash = req.account.addressHash();
        Bytes path = bytesToPath(req.slotKey.getSlotHash());
        List<Bytes> slices = new ArrayList<>();
        for (int i = 1; i <= path.size(); i++) {
          slices.add(Bytes.concatenate(accountHash, path.slice(0, i)));
        }
        allPaths.add(slices);
        allIsAccountList.add(false);

        List<Integer> markers = new ArrayList<>();
        for (int i = 0; i < slices.size(); i += STRIDE) markers.add(i);
        if (markers.get(markers.size() - 1) != slices.size() - 1) {
          markers.add(slices.size() - 1);
        }
        allMarkerIndicesList.add(markers);
        for (int i : markers) firstPassKeys.add(slices.get(i).toArrayUnsafe());
      }

      List<byte[]> firstValues = storage.getMultipleKeys(firstPassKeys);

      List<byte[]> secondPassKeys = new ArrayList<>();
      List<Boolean> secondPassIsAccount = new ArrayList<>();
      int keyIdx = 0;

      for (int i = 0; i < allPaths.size(); i++) {
        List<Bytes> slices = allPaths.get(i);
        List<Integer> markers = allMarkerIndicesList.get(i);
        boolean isAccount = allIsAccountList.get(i);
        Set<Integer> live = new HashSet<>();

        for (int m : markers) {
          byte[] val = firstValues.get(keyIdx++);
          if (val != null) {
            Bytes node = Bytes.wrap(val);
            if (isAccount) accountNodes.put(Hash.hash(node), node);
            else storageNodes.put(Hash.hash(node), node);
            live.add(m);
          }
        }

        for (int j = 0; j < markers.size() - 1; j++) {
          int left = markers.get(j);
          int right = markers.get(j + 1);
          if (!live.contains(left) && !live.contains(right)) continue;
          for (int k = left + 1; k < right; k++) {
            secondPassKeys.add(slices.get(k).toArrayUnsafe());
            secondPassIsAccount.add(isAccount);
          }
        }
      }

      if (!secondPassKeys.isEmpty()) {
        List<byte[]> secondValues = storage.getMultipleKeys(secondPassKeys);
        for (int i = 0; i < secondValues.size(); i++) {
          byte[] val = secondValues.get(i);
          if (val != null) {
            Bytes node = Bytes.wrap(val);
            if (secondPassIsAccount.get(i)) accountNodes.put(Hash.hash(node), node);
            else storageNodes.put(Hash.hash(node), node);
          }
        }
      }
    } finally {
      storage.unSubscribe(subscriptionId);
      timer.stopTimer();
    }
  }

  public Optional<Bytes> getAccountStateTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(accountNodes.getIfPresent(nodeHash))
          .or(
              () -> {
                accountCacheMissCounter.inc();
                return worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash);
              });
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(storageNodes.getIfPresent(nodeHash))
          .or(
              () -> {
                storageCacheMissCounter.inc();
                return worldStateKeyValueStorage.getAccountStorageTrieNode(
                    accountHash, location, nodeHash);
              });
    }
  }

  public Preloader getPreloader() {
    return preloader;
  }
}
