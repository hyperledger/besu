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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.api.TopicsParameter;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;

public class FilterParameter {

  private final BlockParameter fromBlock;
  private final BlockParameter toBlock;
  private final List<Address> addresses;
  private final TopicsParameter topics;
  private final Hash blockhash;

  @JsonCreator
  public FilterParameter(
      @JsonProperty("fromBlock") final String fromBlock,
      @JsonProperty("toBlock") final String toBlock,
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("address")
          final List<String> address,
      @JsonDeserialize(using = TopicsDeserializer.class) @JsonProperty("topics")
          final TopicsParameter topics,
      @JsonProperty("blockhash") final String blockhash) {
    this.fromBlock =
        fromBlock != null ? new BlockParameter(fromBlock) : new BlockParameter("latest");
    this.toBlock = toBlock != null ? new BlockParameter(toBlock) : new BlockParameter("latest");
    this.addresses = address != null ? renderAddress(address) : Collections.emptyList();
    this.topics = topics != null ? topics : new TopicsParameter(Collections.emptyList());
    this.blockhash = blockhash != null ? Hash.fromHexString(blockhash) : null;
  }

  private List<Address> renderAddress(final List<String> inputAddresses) {
    final List<Address> addresses = new ArrayList<>();
    for (final String value : inputAddresses) {
      addresses.add(Address.fromHexString(value));
    }
    return addresses;
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

  public TopicsParameter getTopics() {
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
