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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes32;

public class PrivacyGroupHeadBlockMap implements Map<Bytes32, Hash> {
  private final HashMap<Bytes32, Hash> map;

  public static final PrivacyGroupHeadBlockMap empty() {
    return new PrivacyGroupHeadBlockMap(Collections.emptyMap());
  }

  public PrivacyGroupHeadBlockMap(final Map<Bytes32, Hash> map) {
    this.map = new HashMap<>(map);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    map.forEach((key, value) -> new RLPMapEntry(key, value).writeTo(out));

    out.endList();
  }

  public static PrivacyGroupHeadBlockMap readFrom(final RLPInput input) {
    final List<RLPMapEntry> entries = input.readList(RLPMapEntry::readFrom);

    final HashMap<Bytes32, Hash> map = new HashMap<>();
    entries.forEach(e -> map.put(Bytes32.wrap(e.getKey()), Hash.wrap(Bytes32.wrap(e.getValue()))));

    return new PrivacyGroupHeadBlockMap(map);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivacyGroupHeadBlockMap that = (PrivacyGroupHeadBlockMap) o;
    return map.equals(that.map);
  }

  public boolean contains(final Bytes32 key, final Hash value) {
    return map.containsKey(key) && map.get(key).equals(value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(map);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(final Object value) {
    return map.containsValue(value);
  }

  @Override
  public Hash get(final Object key) {
    return map.get(key);
  }

  @Override
  public Hash put(final Bytes32 key, final Hash value) {
    return map.put(key, value);
  }

  @Override
  public Hash remove(final Object key) {
    return map.remove(key);
  }

  @Override
  public void putAll(final Map<? extends Bytes32, ? extends Hash> m) {
    map.putAll(m);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public Set<Bytes32> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<Hash> values() {
    return map.values();
  }

  @Override
  public Set<Entry<Bytes32, Hash>> entrySet() {
    return map.entrySet();
  }
}
