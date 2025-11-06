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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Collects world-state write operations that can be replayed against a storage updater. */
public class WorldStateWriteSet {

  private final List<Consumer<TrieWriteSink>> operations = new ArrayList<>();
  private Optional<Hash> computedRoot = Optional.empty();

  public Hash computedRoot() {
    if (computedRoot.isEmpty()) {
      throw new IllegalStateException("Computed root not set on write set");
    }
    return computedRoot.get();
  }

  public void setComputedRoot(final Hash root) {
    if (computedRoot.isPresent()) {
      throw new IllegalStateException("Computed root already set on write set");
    }
    computedRoot = Optional.of(root);
  }

  public void record(final Consumer<TrieWriteSink> operation) {
    operations.add(operation);
  }

  public void applyTo(final WorldStateKeyValueStorage.Updater updater) {
    final TrieWriteSink sink = updater.getTrieWriteSink();
    operations.forEach(operation -> operation.accept(sink));
  }
}
