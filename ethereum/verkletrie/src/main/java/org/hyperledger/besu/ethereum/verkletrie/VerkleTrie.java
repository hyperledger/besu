/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.verkle.StoredVerkleTrie;
import org.hyperledger.besu.ethereum.trie.verkle.factory.StoredNodeFactory;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VerkleTrie {

  private final org.hyperledger.besu.ethereum.trie.verkle.VerkleTrie<Bytes, Bytes> verkleTrie;

  private final StoredNodeFactory<Bytes> nodeFactory;

  public VerkleTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    nodeFactory = new StoredNodeFactory<>(nodeLoader, value -> value);
    verkleTrie = new StoredVerkleTrie<>(nodeFactory);
  }

  public Optional<Bytes> get(final Bytes key) {
    return verkleTrie.get(key);
  }

  public Optional<Bytes> put(final Bytes key, final Bytes value) {
    return verkleTrie.put(key, Bytes32.leftPad(value));
  }

  public void remove(final Bytes key) {
    verkleTrie.remove(Bytes32.wrap(key));
  }

  public Bytes32 getRootHash() {
    return verkleTrie.getRootHash();
  }

  public void commit(final NodeUpdater nodeUpdater) {
    verkleTrie.commit(nodeUpdater);
  }
}
