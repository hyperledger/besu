/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

interface Node<V> {

  Node<V> accept(PathNodeVisitor<V> visitor, BytesValue path);

  void accept(NodeVisitor<V> visitor);

  BytesValue getPath();

  Optional<V> getValue();

  BytesValue getRlp();

  BytesValue getRlpRef();

  Bytes32 getHash();

  Node<V> replacePath(BytesValue path);

  /** Marks the node as needing to be persisted */
  void markDirty();

  /** @return True if the node needs to be persisted. */
  boolean isDirty();

  String print();
}
