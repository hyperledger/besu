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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;

public class TrieNodePreLoader implements StorageSubscriber {

  private static final int CACHE_SIZE = 300_000;
  private final Cache<Bytes, Bytes> nodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(CACHE_SIZE).build();
  private final Cache<Bytes, Optional<Bytes>> stems =
      CacheBuilder.newBuilder().recordStats().maximumSize(CACHE_SIZE).build();

  private final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage;

  public TrieNodePreLoader(final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage) {
    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
  }

  @VisibleForTesting
  public void cacheNodes(final Bytes stem) {
    final long storageSubscriberId = worldStateKeyValueStorage.subscribe(this);
    try {
      // The stem represents the path in the tree, so we will load all the nodes along the path.
      Optional<Bytes> node;

      // we start with the stem
      getStem(stem);

      Bytes location = Bytes.EMPTY;
      do {
        node = getStateTrieNode(location);
        location = stem.slice(0, location.size() + 1);
      } while (node.isPresent());
    } finally {
      worldStateKeyValueStorage.unSubscribe(storageSubscriberId);
    }
  }

  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    return worldStateKeyValueStorage.getStateTrieNode(location);
  }

  public Optional<Bytes> getStem(final Bytes stem) {
    final Optional<Bytes> cachedStem = stems.getIfPresent(stem);
    if (cachedStem != null) {
      return cachedStem;
    } else {
      return worldStateKeyValueStorage.getStem(stem);
    }
  }

  public Optional<List<Bytes32>> getDecodedStem(final Bytes stem) {
    return getStem(stem).map(this::decodeStemNode);
  }

  public void reset() {
    nodes.invalidateAll();
  }

  @Override
  public void onClearTrie() {
    reset();
  }

  private List<Bytes32> decodeStemNode(final Bytes encodedValues) {
    RLPInput input = new BytesValueRLPInput(encodedValues, false);
    input.enterList();
    input.skipNext(); // depth
    input.skipNext(); // commitment
    input.skipNext(); // leftCommitment
    input.skipNext(); // rightCommitment
    input.skipNext(); // leftScalar
    input.skipNext(); // rightScalar
    return input.readList(
            rlpInput -> Bytes32.leftPad(rlpInput.readBytes()));
  }

}
