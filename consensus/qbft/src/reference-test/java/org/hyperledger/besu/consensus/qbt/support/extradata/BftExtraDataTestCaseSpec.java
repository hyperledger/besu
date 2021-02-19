package org.hyperledger.besu.consensus.qbt.support.extradata;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BftExtraDataTestCaseSpec {
  final Map<String, Object> bftExtraData;
  final String rlpAll;
  final String rlpExcludeCommitSeals;
  final String rlpExcludeCommitSealsAndRoundNumber;

  @JsonCreator
  public BftExtraDataTestCaseSpec(
      @JsonProperty("qbft_extra_data") final Map<String, Object> bftExtraData,
      @JsonProperty("rlp_all") final String rlpAll,
      @JsonProperty("rlp_exclude_commit_seals") final String rlpExcludeCommitSeals,
      @JsonProperty("rlp_exclude_commit_seals_and_round_number")
          final String rlpExcludeCommitSealsAndRoundNumber) {
    this.bftExtraData = bftExtraData;
    this.rlpAll = rlpAll;
    this.rlpExcludeCommitSeals = rlpExcludeCommitSeals;
    this.rlpExcludeCommitSealsAndRoundNumber = rlpExcludeCommitSealsAndRoundNumber;
  }

  public Map<String, Object> getBftExtraData() {
    return bftExtraData;
  }

  public String getRlpAll() {
    return rlpAll;
  }

  public String getRlpExcludeCommitSeals() {
    return rlpExcludeCommitSeals;
  }

  public String getRlpExcludeCommitSealsAndRoundNumber() {
    return rlpExcludeCommitSealsAndRoundNumber;
  }
}
