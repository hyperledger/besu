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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.internal.StorageEntry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class SendRawTransactionConditionalParameter {

  private final Optional<Long> fromBlock;
  private final Optional<Long> toBlock;
  // TODO may need a custom deserializer because the hash here could actually be a map
  private final Optional<Map<Address, KnownAccountInfo>> knownAccounts;
  private final Optional<Long> timestampMin, timestampMax;

  @JsonCreator
  public SendRawTransactionConditionalParameter(
      @JsonProperty("blockNumberMin") final Long fromBlock,
      @JsonProperty("blockNumberMax") final Long toBlock,
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          @JsonDeserialize(using = KnownAccountsInfoDeserializer.class)
          @JsonProperty("knownAccounts")
          final Map<Address, KnownAccountInfo> knownAccounts,
      @JsonProperty("timestampMin") final Long timestampMin,
      @JsonProperty("timestampMax") final Long timestampMax) {
    this.fromBlock = Optional.ofNullable(fromBlock);
    this.toBlock = Optional.ofNullable(toBlock);
    this.knownAccounts = Optional.ofNullable(knownAccounts);
    this.timestampMin = Optional.ofNullable(timestampMin);
    this.timestampMax = Optional.ofNullable(timestampMax);
  }

  public Optional<Long> getBlockNumberMin() {
    return fromBlock;
  }

  public Optional<Long> getBlockNumberMax() {
    return toBlock;
  }

  public Optional<Map<Address, KnownAccountInfo>> getKnownAccounts() {
    return knownAccounts;
  }

  public Optional<Long> getTimestampMin() {
    return timestampMin;
  }

  public Optional<Long> getTimestampMax() {
    return timestampMax;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SendRawTransactionConditionalParameter that = (SendRawTransactionConditionalParameter) o;
    return Objects.equals(fromBlock, that.fromBlock)
        && Objects.equals(toBlock, that.toBlock)
        && Objects.equals(knownAccounts, that.knownAccounts)
        && Objects.equals(timestampMin, that.timestampMin)
        && Objects.equals(timestampMax, that.timestampMax);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromBlock, toBlock, knownAccounts, timestampMin, timestampMax);
  }

  @Override
  public String toString() {
    return "SendRawTransactionConditionalParameter{"
        + "fromBlock="
        + fromBlock
        + ", toBlock="
        + toBlock
        + ", knownAccounts="
        + knownAccounts
        + ", timestampMin="
        + timestampMin
        + ", timestampMax="
        + timestampMax
        + '}';
  }

  public static class KnownAccountInfo {
    // either a hash, or a map of storage slot to value
    @SuppressWarnings("UnusedVariable")
    private Hash storageRootHash;

    @SuppressWarnings("UnusedVariable")
    private List<StorageEntry> expectedStorageEntries;

    public KnownAccountInfo(final Hash hash) {
      this.storageRootHash = hash;
    }

    public KnownAccountInfo(final List<StorageEntry> storageEntryList) {
      this.expectedStorageEntries = storageEntryList;
    }
  }

  public static class KnownAccountsInfoDeserializer
      extends StdDeserializer<Map<Address, KnownAccountInfo>> {
    public KnownAccountsInfoDeserializer() {
      this(null);
    }

    public KnownAccountsInfoDeserializer(final Class<?> vc) {
      super(vc);
    }

    @Override
    public Map<Address, KnownAccountInfo> deserialize(
        final JsonParser jsonParser, final DeserializationContext context) throws IOException {
      final Map<Address, KnownAccountInfo> knownAccountsInfoMap = new HashMap<>();
      final ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
      final JsonNode rootNode = jsonParser.getCodec().readTree(jsonParser);
      if (rootNode.isObject()) {
        // field names of the rootNode should be the addresses of the known accounts

        for (Iterator<String> it = rootNode.fieldNames(); it.hasNext(); ) {
          String key = it.next();
          final Address address = Address.fromHexString(key);
          final JsonNode infoNode = rootNode.get(key);
          if (infoNode.isContainerNode()) {
            // collect storage entries
            final List<StorageEntry> storageEntryList = Lists.newArrayList();
            for (Iterator<String> it2 = infoNode.fieldNames(); it2.hasNext(); ) {
              final String slotKey = it2.next();
              final JsonNode slotValue = infoNode.get(slotKey);
              final StorageEntry storageEntry =
                  new StorageEntry(
                      UInt256.fromHexString(slotKey), Bytes.fromHexString(slotValue.textValue()));
              storageEntryList.add(storageEntry);
            }
            knownAccountsInfoMap.put(address, new KnownAccountInfo(storageEntryList));
          } else {
            // string representing the storage root hash
            knownAccountsInfoMap.put(
                address, new KnownAccountInfo(mapper.readValue(infoNode.toString(), Hash.class)));
          }
        }
      }
      return knownAccountsInfoMap;
    }
  }
}
