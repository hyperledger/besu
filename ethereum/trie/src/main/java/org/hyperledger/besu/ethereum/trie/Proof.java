/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.trie;

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

public class Proof<V> {

  private final Optional<V> value;

  private final List<BytesValue> proofRelatedNodes;

  public Proof(final Optional<V> value, final List<BytesValue> proofRelatedNodes) {
    this.value = value;
    this.proofRelatedNodes = proofRelatedNodes;
  }

  public Optional<V> getValue() {
    return value;
  }

  public List<BytesValue> getProofRelatedNodes() {
    return proofRelatedNodes;
  }
}
