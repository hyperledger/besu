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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PrivateGroupIdToLatestBlockWithTransactionMap {
  private final Map<Bytes32, Hash> map;

  public static final PrivateGroupIdToLatestBlockWithTransactionMap EMPTY =
      new PrivateGroupIdToLatestBlockWithTransactionMap(new HashMap<>());

  public PrivateGroupIdToLatestBlockWithTransactionMap(final Map<Bytes32, Hash> map) {
    this.map = map;
  }

  public Map<Bytes32, Hash> getMap() {
    return map;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    map.entrySet()
        .forEach(
            e -> {
              out.startList();
              out.writeBytesValue(e.getKey());
              out.writeBytesValue(e.getValue());
              out.endList();
            });

    out.endList();
  }

  public static PrivateGroupIdToLatestBlockWithTransactionMap readFrom(final RLPInput input) {
    final List<PrivateGroupIdBlockHashMapEntry> entries =
        input.readList(PrivateGroupIdBlockHashMapEntry::readFrom);

    final HashMap<Bytes32, Hash> map = new HashMap<>();
    entries.stream().forEach(e -> map.put(e.getPrivacyGroup(), e.getBlockHash()));

    return new PrivateGroupIdToLatestBlockWithTransactionMap(map);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivateGroupIdToLatestBlockWithTransactionMap that =
        (PrivateGroupIdToLatestBlockWithTransactionMap) o;
    return map.equals(that.map);
  }

  @Override
  public int hashCode() {
    return Objects.hash(map);
  }
}
