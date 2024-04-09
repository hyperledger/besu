package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.trie.verkle.SuffixStateDiff;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes32;

public class SuffixStateDiffParameter {

  private final byte suffix;
  private final Bytes32 currentValue;
  private final Bytes32 newValue;

  @JsonCreator
  public SuffixStateDiffParameter(
      @JsonProperty("suffix") final byte suffix,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("currentValue")
          final Bytes32 currentValue,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("newValue")
          final Bytes32 newValue) {
    this.suffix = suffix;
    this.currentValue = currentValue;
    this.newValue = newValue;
  }

  public static List<SuffixStateDiffParameter> fromSuffixStateDiff(
      final List<SuffixStateDiff> suffixStateDiffList) {
    final List<SuffixStateDiffParameter> suffixStateDiffParameterList =
        new ArrayList<SuffixStateDiffParameter>();
    for (SuffixStateDiff suffixStateDiff : suffixStateDiffList) {
      suffixStateDiffParameterList.add(
          new SuffixStateDiffParameter(
              suffixStateDiff.suffix(),
              suffixStateDiff.currentValue(),
              suffixStateDiff.newValue()));
    }
    return suffixStateDiffParameterList;
  }

  public static List<SuffixStateDiff> toSuffixStateDiff(
      final List<SuffixStateDiffParameter> suffixStateDiffParameterList) {
    final List<SuffixStateDiff> suffixStateDiffsList = new ArrayList<SuffixStateDiff>();
    for (SuffixStateDiffParameter suffixStateDiffParameter : suffixStateDiffParameterList) {
      suffixStateDiffsList.add(
          new SuffixStateDiff(
              suffixStateDiffParameter.getSuffix(),
              suffixStateDiffParameter.getCurrentValue(),
              suffixStateDiffParameter.getNewValue()));
    }
    return suffixStateDiffsList;
  }

  @Override
  public String toString() {
    return "SuffixStateDiff{" + "suffix=" + suffix + ", currentValue=" + currentValue;
  }

  @JsonGetter
  public byte getSuffix() {
    return suffix;
  }

  @JsonGetter
  public Bytes32 getCurrentValue() {
    return currentValue;
  }

  @JsonGetter
  public Bytes32 getNewValue() {
    return newValue;
  }
}
