package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface BftExtraData extends ParsedExtraData {

  int EXTRA_VANITY_LENGTH = 32;

  Bytes encode();

  Bytes encodeWithoutCommitSeals();

  Bytes encodeWithoutCommitSealsAndRoundNumber();

  Bytes getVanityData();

  Collection<SECPSignature> getSeals();

  Collection<Address> getValidators();

  Optional<Vote> getVote();

  int getRound();
}
