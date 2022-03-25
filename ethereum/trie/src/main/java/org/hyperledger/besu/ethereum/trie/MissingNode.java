/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.trie;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MissingNode<V> extends NullNode<V> {

  private final Bytes32 hash;
  private final Bytes location;
  private final Bytes path;

  public MissingNode(final Bytes32 hash, final Bytes location) {
    this.hash = hash;
    this.location = location;
    this.path = (location == Bytes.EMPTY) ? Bytes.EMPTY : location.slice(0, location.size() - 1);
  }

  @Override
  public Bytes32 getHash() {
    return hash;
  }

  @Override
  public Bytes getPath() {
    return path;
  }

  @Override
  public boolean isHealNeeded() {
    return true;
  }

  @Override
  public Optional<Bytes> getLocation() {
    return Optional.ofNullable(location);
  }
}
