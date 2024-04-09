package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.trie.verkle.StateDiff;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

public class StateDiffParameter {

  final List<StemStateDiffParameter> steamStateDiff;

  @JsonCreator
  public StateDiffParameter(final List<StemStateDiffParameter> steamStateDiff) {
    this.steamStateDiff = steamStateDiff;
  }

  public List<StemStateDiffParameter> getSteamStateDiff() {
    return steamStateDiff;
  }

  public static StateDiff toStateDiff(final StateDiffParameter stateDiffParameter) {
    return new StateDiff(
        StemStateDiffParameter.toListOfStemStateDiff(stateDiffParameter.getSteamStateDiff()));
  }

  public static StateDiffParameter fromStateDiff(final StateDiff stateDiff) {
    return new StateDiffParameter(
        StemStateDiffParameter.fromListOfStemStateDiff(stateDiff.steamStateDiff()));
  }

  @Override
  public String toString() {
    return "StateDiffParameter{" + "steamStateDiff=" + steamStateDiff + '}';
  }
}
