package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.ethereum.core.Address;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

abstract class VoteMixin {

  @JsonCreator
  VoteMixin(
      @JsonProperty("recipient") final Address recipient,
      @JsonProperty("voteType") final VoteType voteType) {}
}
