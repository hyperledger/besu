/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class SnapStorageEntriesCollector extends StorageEntriesCollector<Bytes> {

  private int currentSize = 0;
  private final Bytes32 endKeyHash;
  private final Integer maxResponseBytes;

  public SnapStorageEntriesCollector(
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int limit,
      final int maxResponseBytes) {
    super(startKeyHash, limit);
    this.endKeyHash = endKeyHash;
    this.maxResponseBytes = maxResponseBytes;
  }

  public static Map<Bytes32, Bytes> collectEntries(
      final Node<Bytes> root,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int limit,
      final int maxResponseBytes) {
    final SnapStorageEntriesCollector entriesCollector =
        new SnapStorageEntriesCollector(startKeyHash, endKeyHash, limit, maxResponseBytes);
    final TrieIterator<Bytes> visitor = new TrieIterator<>(entriesCollector, false);
    root.accept(visitor, CompactEncoding.bytesToPath(startKeyHash));
    return entriesCollector.getValues();
  }

  @Override
  public TrieIterator.State onLeaf(final Bytes32 keyHash, final Node<Bytes> node) {
    if (keyHash.compareTo(startKeyHash) >= 0) {
      if (node.getValue().isPresent()) {
        final Bytes value = node.getValue().get();
        currentSize += Bytes32.SIZE + value.size();
        if (currentSize > maxResponseBytes) {
          return TrieIterator.State.STOP;
        }
        if (!values.isEmpty() && keyHash.compareTo(endKeyHash) > 0) {
          return TrieIterator.State.STOP;
        }

        values.put(keyHash, value);
      }
    }
    return limitReached() ? TrieIterator.State.STOP : TrieIterator.State.CONTINUE;
  }
}
