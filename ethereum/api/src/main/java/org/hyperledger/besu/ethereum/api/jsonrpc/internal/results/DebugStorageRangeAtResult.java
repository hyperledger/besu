/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.evm.account.AccountStorageEntry;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class DebugStorageRangeAtResult implements JsonRpcResult {

  private final NavigableMap<String, StorageEntry> storage = new TreeMap<>();
  private final String nextKey;

  public DebugStorageRangeAtResult(
      final NavigableMap<Bytes32, AccountStorageEntry> entries,
      final Bytes32 nextKey,
      final boolean shortValues) {
    if (shortValues) {
      entries.forEach(
          (keyHash, entry) ->
              storage.put(
                  toStrictShortHexString(keyHash.toString()),
                  new StorageEntry(entry, shortValues)));
      this.nextKey = nextKey != null ? nextKey.toString() : null;

    } else {
      entries.forEach(
          (keyHash, entry) ->
              storage.put(keyHash.toString(), new StorageEntry(entry, shortValues)));
      this.nextKey = nextKey != null ? nextKey.toString() : null;
    }
  }

  private static String toStrictShortHexString(final String hex) {
    // Skipping '0x'
    if (hex.charAt(2) != '0') return hex;

    int i = 3;
    while (i < hex.length() - 1 && hex.charAt(i) == '0') {
      i++;
    }
    // Align the trim so we get full bytes, not stray nybbles.
    i = i & 0xFFFFFFFE;

    return "0x" + hex.substring(i);
  }

  @JsonGetter(value = "storage")
  public NavigableMap<String, StorageEntry> getStorage() {
    return storage;
  }

  @JsonGetter(value = "nextKey")
  public String getNextKey() {
    return nextKey;
  }

  @JsonGetter(value = "complete")
  public boolean getComplete() {
    return nextKey == null;
  }

  @JsonPropertyOrder(value = {"key", "value"})
  public static class StorageEntry {
    private final String value;
    private final String key;

    public StorageEntry(final AccountStorageEntry entry, final boolean shortValues) {
      if (shortValues) {
        this.value = entry.getValue().toMinimalBytes().toHexString();
        this.key =
            entry
                .getKey()
                .map(UInt256::toMinimalBytes)
                .map(Bytes::toHexString)
                .map(s -> "0x".equals(s) ? "0x00" : s)
                .orElse(null);
      } else {
        this.value = entry.getValue().toHexString();
        this.key = entry.getKey().map(UInt256::toHexString).orElse(null);
      }
    }

    @JsonGetter(value = "key")
    public String getKey() {
      return key;
    }

    @JsonGetter(value = "value")
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("key", key).add("value", value).toString();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final StorageEntry that = (StorageEntry) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }
}
