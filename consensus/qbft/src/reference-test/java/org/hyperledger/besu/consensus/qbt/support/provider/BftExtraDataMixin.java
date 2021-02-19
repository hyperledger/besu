package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

abstract class BftExtraDataMixin {
  @JsonCreator
  BftExtraDataMixin(
      @JsonProperty("vanityData") final Bytes vanityData,
      @JsonProperty("seals") final Collection<SECP256K1.Signature> seals,
      @JsonProperty("vote") final Optional<Vote> vote,
      @JsonProperty("round") final int round,
      @JsonProperty("validators") final Collection<Address> validators) {}
}
