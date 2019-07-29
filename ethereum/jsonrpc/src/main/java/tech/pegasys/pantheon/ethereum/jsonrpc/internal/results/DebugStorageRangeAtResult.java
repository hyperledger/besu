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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.core.AccountStorageEntry;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;

public class DebugStorageRangeAtResult implements JsonRpcResult {

  private final NavigableMap<String, StorageEntry> storage = new TreeMap<>();
  private final String nextKey;

  public DebugStorageRangeAtResult(
      final NavigableMap<Bytes32, AccountStorageEntry> entries, final Bytes32 nextKey) {
    entries.forEach((keyHash, entry) -> storage.put(keyHash.toString(), new StorageEntry(entry)));
    this.nextKey = nextKey != null ? nextKey.toString() : null;
  }

  @JsonGetter(value = "storage")
  public NavigableMap<String, StorageEntry> getStorage() {
    return storage;
  }

  @JsonGetter(value = "nextKey")
  public String getNextKey() {
    return nextKey;
  }

  @JsonPropertyOrder(value = {"key", "value"})
  public static class StorageEntry {
    private final String value;
    private final String key;

    public StorageEntry(final AccountStorageEntry entry) {
      this.value = entry.getValue().toHexString();
      this.key = entry.getKey().map(UInt256::toHexString).orElse(null);
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
      return MoreObjects.toStringHelper(this).add("value", value).toString();
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
