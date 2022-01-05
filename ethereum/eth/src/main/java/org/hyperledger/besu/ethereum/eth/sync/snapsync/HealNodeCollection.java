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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class HealNodeCollection extends ConcurrentHashMap<Bytes, Bytes> {

  private static final int MAX_SIZE = 1_000_000;

  @Override
  public Bytes put(@NotNull final Bytes key, @NotNull final Bytes value) {
    if (size() > MAX_SIZE) {
      return null;
    }
    return super.put(key, value);
  }

  public Optional<Bytes> get(@NotNull final Bytes key, @NotNull final Bytes hash) {
    return Optional.ofNullable(super.get(key))
        .filter(bytes -> Hash.hash(bytes).compareTo(hash) == 0);
  }
}
