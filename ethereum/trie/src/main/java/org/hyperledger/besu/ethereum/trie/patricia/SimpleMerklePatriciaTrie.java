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
package org.hyperledger.besu.ethereum.trie.patricia;

import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.SimpleMerkleTrie;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

/**
 * An in-memory {@link MerkleTrie}.
 *
 * @param <V> The type of values stored by this trie.
 */
public class SimpleMerklePatriciaTrie<K extends Bytes, V> extends SimpleMerkleTrie<K, V>
    implements MerkleTrie<K, V> {
  private final GetVisitor<V> getVisitor = new GetVisitor<>();
  private final RemoveVisitor<V> removeVisitor = new RemoveVisitor<>();

  public SimpleMerklePatriciaTrie(final Function<V, Bytes> valueSerializer) {
    super(valueSerializer);
  }

  @Override
  public GetVisitor<V> getGetVisitor() {
    return getVisitor;
  }

  @Override
  public RemoveVisitor<V> getRemoveVisitor() {
    return removeVisitor;
  }

  @Override
  public PathNodeVisitor<V> getPutVisitor(final V value) {
    return new PutVisitor<>(nodeFactory, value);
  }
}
