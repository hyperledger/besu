/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

public class ProtocolSpecAdapters {

  final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> modifiers;

  public ProtocolSpecAdapters(
      final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> modifiers) {
    this.modifiers = modifiers;
  }

  public static ProtocolSpecAdapters create(
      final long block, final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> entries = new HashMap<>();
    entries.put(block, modifier);
    return new ProtocolSpecAdapters(entries);
  }

  public Function<ProtocolSpecBuilder, ProtocolSpecBuilder> getModifierForBlock(
      final long blockNumber) {
    final NavigableSet<Long> epochs = new TreeSet<>(modifiers.keySet());
    final Long modifier = epochs.floor(blockNumber);

    if (modifier == null) {
      return Function.identity();
    }

    return modifiers.get(modifier);
  }

  public Stream<Map.Entry<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>>> stream() {
    return modifiers.entrySet().stream();
  }
}
