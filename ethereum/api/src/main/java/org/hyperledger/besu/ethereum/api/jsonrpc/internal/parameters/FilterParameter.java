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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;

import java.util.List;
import java.util.Optional;

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
  private final Hash blockhash;

  @JsonCreator
  public FilterParameter(
      @JsonProperty("fromBlock") final String fromBlock,
      @JsonProperty("toBlock") final String toBlock,
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("address")
          final List<Address> address,
      @JsonDeserialize(using = TopicsDeserializer.class) @JsonProperty("topics")
          final List<List<LogTopic>> topics,
      @JsonProperty("blockhash") final String blockhash) {
    this.fromBlock =
        fromBlock != null ? new BlockParameter(fromBlock) : new BlockParameter("latest");
    this.toBlock = toBlock != null ? new BlockParameter(toBlock) : new BlockParameter("latest");
    this.addresses = Optional.ofNullable(address).orElse(emptyList());
    this.topics = Optional.ofNullable(topics).orElse(emptyList());
    this.blockhash = blockhash != null ? Hash.fromHexString(blockhash) : null;
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

  public Hash getBlockhash() {
    return blockhash;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fromBlock", fromBlock)
        .add("toBlock", toBlock)
        .add("addresses", addresses)
        .add("topics", topics)
        .add("blockhash", blockhash)
        .toString();
  }
}
