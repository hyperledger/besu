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
package org.hyperledger.besu.ethereum.trie.binary;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.MerkleStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerkleTrie;

import java.util.function.Function;

/**
 * A {@link MerkleTrie} that persists trie nodes to a {@link MerkleStorage} key/value store.
 *
 * @param <V> The type of values stored by this trie.
 */
public class StoredBinaryMerkleTrie<K extends Bytes, V> extends StoredMerkleTrie<K, V> implements MerkleTrie<K, V> {

  private final GetVisitor<V> getVisitor = new GetVisitor<>();

  public StoredBinaryMerkleTrie(final NodeLoader nodeLoader, final Function<V, Bytes> valueSerializer, final Function<Bytes, V> valueDeserializer) {
    super(new StoredNodeFactory<>(nodeLoader, valueSerializer, valueDeserializer));
  }

  public StoredBinaryMerkleTrie(final NodeLoader nodeLoader, final Bytes32 rootHash, final Bytes rootLocation, final Function<V, Bytes> valueSerializer, final Function<Bytes, V> valueDeserializer) {
    super(new StoredNodeFactory<>(nodeLoader, valueSerializer, valueDeserializer),  rootHash, rootLocation);
  }

  public StoredBinaryMerkleTrie(final NodeLoader nodeLoader, final Bytes32 rootHash, final Function<V, Bytes> valueSerializer, final Function<Bytes, V> valueDeserializer) {
    super(new StoredNodeFactory<>(nodeLoader, valueSerializer, valueDeserializer), rootHash);
  }

  public StoredBinaryMerkleTrie(final StoredNodeFactory<V> nodeFactory, final Bytes32 rootHash) {
    super(nodeFactory, rootHash);
  }


  @Override
  public PathNodeVisitor<V> getGetVisitor() {
    return getVisitor;
  }

  @Override
  public PathNodeVisitor<V> getRemoveVisitor() {
    throw new UnsupportedOperationException ("cannot remove in the sparse merkle trie");
  }

  @Override
  public PathNodeVisitor<V> getPutVisitor(final V value) {
    return new PutVisitor<>(nodeFactory, value);
  }
}
