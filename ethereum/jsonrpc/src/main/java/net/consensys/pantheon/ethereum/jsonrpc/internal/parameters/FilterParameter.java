package net.consensys.pantheon.ethereum.jsonrpc.internal.parameters;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
      @JsonProperty("topics") final List<List<String>> topics,
      @JsonProperty("blockhash") final String blockhash) {
    this.fromBlock =
        fromBlock != null ? new BlockParameter(fromBlock) : new BlockParameter("latest");
    this.toBlock = toBlock != null ? new BlockParameter(toBlock) : new BlockParameter("latest");
    this.addresses = address != null ? renderAddress(address) : Collections.emptyList();
    this.topics =
        topics != null ? new TopicsParameter(topics) : new TopicsParameter(Collections.emptyList());
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
