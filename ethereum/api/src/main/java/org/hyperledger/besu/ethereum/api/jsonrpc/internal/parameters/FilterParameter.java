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

import static java.util.Collections.emptyList;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.core.LogTopic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;

public class FilterParameter {

  private final BlockParameter fromBlock;
  private final BlockParameter toBlock;
  private final List<Address> addresses;
  private final List<List<LogTopic>> topics;
  private final Optional<Hash> maybeBlockHash;
  private final LogsQuery logsQuery;
  private final boolean isValid;

  @JsonCreator
  public FilterParameter(
      @JsonProperty("fromBlock") final BlockParameter fromBlock,
      @JsonProperty("toBlock") final BlockParameter toBlock,
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("address")
          final List<Address> address,
      @JsonDeserialize(using = TopicsDeserializer.class) @JsonProperty("topics")
          final List<List<LogTopic>> topics,
      @JsonProperty("blockHash") @JsonAlias({"blockhash"}) final Hash blockHash) {
    this.isValid = blockHash == null || (fromBlock == null && toBlock == null);
    this.fromBlock = fromBlock != null ? fromBlock : BlockParameter.LATEST;
    this.toBlock = toBlock != null ? toBlock : BlockParameter.LATEST;
    this.addresses = address != null ? address : emptyList();
    this.topics = topics != null ? topics : emptyList();
    this.logsQuery = new LogsQuery(addresses, topics);
    this.maybeBlockHash = Optional.ofNullable(blockHash);
  }

  public BlockParameter getFromBlock() {
    return fromBlock;
  }

  public BlockParameter getToBlock() {
    return toBlock;
  }

  public List<Address> getAddresses() {
    return addresses;
  }

  public List<List<LogTopic>> getTopics() {
    return topics;
  }

  public Optional<Hash> getBlockHash() {
    return maybeBlockHash;
  }

  public LogsQuery getLogsQuery() {
    return logsQuery;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilterParameter that = (FilterParameter) o;
    return fromBlock.equals(that.fromBlock)
        && toBlock.equals(that.toBlock)
        && addresses.equals(that.addresses)
        && topics.equals(that.topics)
        && maybeBlockHash.equals(that.maybeBlockHash)
        && logsQuery.equals(that.logsQuery);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromBlock, toBlock, addresses, topics, maybeBlockHash, logsQuery);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fromBlock", fromBlock)
        .add("toBlock", toBlock)
        .add("addresses", addresses)
        .add("topics", topics)
        .add("blockHash", maybeBlockHash)
        .toString();
  }

  public boolean isValid() {
    return isValid;
  }
}
