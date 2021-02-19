package org.hyperledger.besu.consensus.qbt.support.extradata;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class BftExtraDataTestCaseSpec {
  final BftExtraData bftExtraData;
  final Bytes rlp;

  @JsonCreator
  public BftExtraDataTestCaseSpec(
      @JsonProperty("qbft_extra_data") final BftExtraData bftExtraData,
      @JsonProperty("rlp") final Bytes rlp) {
    this.bftExtraData = bftExtraData;
    this.rlp = rlp;
  }

  public BftExtraData getBftExtraData() {
    return bftExtraData;
  }

  public Bytes getRlp() {
    return rlp;
  }
}
