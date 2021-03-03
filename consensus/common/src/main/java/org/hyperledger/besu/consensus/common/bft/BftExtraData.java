package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class BftExtraData implements ParsedExtraData {
  private final Bytes vanityData;
  private final Collection<SECPSignature> seals;
  private final Collection<Address> validators;
  private final Optional<Vote> vote;
  private final int round;

  public BftExtraData(
      final Bytes vanityData,
      final Collection<SECPSignature> seals,
      final Optional<Vote> vote,
      final int round,
      final Collection<Address> validators) {
    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);
    this.vanityData = vanityData;
    this.seals = seals;
    this.validators = validators;
    this.vote = vote;
    this.round = round;
  }

  public Bytes getVanityData() {
    return vanityData;
  }

  public Collection<SECPSignature> getSeals() {
    return seals;
  }

  public Collection<Address> getValidators() {
    return validators;
  }

  public Optional<Vote> getVote() {
    return vote;
  }

  public int getRound() {
    return round;
  }
}
