package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.trie.verkle.StemStateDiff;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;

public class StemStateDiffParameter {
  final Bytes stem;
  final List<SuffixStateDiffParameter> suffixDiffs;

  @JsonCreator
  public StemStateDiffParameter(
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("stem") final Bytes stem,
      @JsonProperty("suffixDiffs") final List<SuffixStateDiffParameter> suffixDiffs) {
    this.stem = stem;
    this.suffixDiffs = suffixDiffs;
  }

  public static List<StemStateDiffParameter> fromListOfStemStateDiff(
      final List<StemStateDiff> stateDiff) {
    List<StemStateDiffParameter> stemStateDiffParameterList =
        new ArrayList<StemStateDiffParameter>();
    for (StemStateDiff stemStateDiff : stateDiff) {
      stemStateDiffParameterList.add(
          new StemStateDiffParameter(
              stemStateDiff.stem(),
              SuffixStateDiffParameter.fromSuffixStateDiff(stemStateDiff.suffixDiffs())));
    }
    return stemStateDiffParameterList;
  }

  public static List<StemStateDiff> toListOfStemStateDiff(
      final List<StemStateDiffParameter> stemStateDiffParameterList) {
    List<StemStateDiff> stemStateDiffs = new ArrayList<StemStateDiff>();
    for (StemStateDiffParameter stemStateDiffParameter : stemStateDiffParameterList) {
      stemStateDiffs.add(
          new StemStateDiff(
              stemStateDiffParameter.getStem(),
              SuffixStateDiffParameter.toSuffixStateDiff(stemStateDiffParameter.getSuffixDiffs())));
    }
    return stemStateDiffs;
  }

  @JsonGetter
  public Bytes getStem() {
    return stem;
  }

  @JsonGetter
  public List<SuffixStateDiffParameter> getSuffixDiffs() {
    return suffixDiffs;
  }
}
