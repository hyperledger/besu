/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;
import java.util.Optional;

public class IbftHelpers {

  public static final Hash EXPECTED_MIX_HASH =
      Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365");

  public static int calculateRequiredValidatorQuorum(final int validatorCount) {
    return Util.fastDivCeiling(2 * validatorCount, 3);
  }

  public static long prepareMessageCountForQuorum(final long quorum) {
    return quorum - 1;
  }

  public static Block createSealedBlock(
      final Block block, final Collection<Signature> commitSeals) {
    final BlockHeader initialHeader = block.getHeader();
    final IbftExtraData initialExtraData = IbftExtraData.decode(initialHeader);

    final IbftExtraData sealedExtraData =
        new IbftExtraData(
            initialExtraData.getVanityData(),
            commitSeals,
            initialExtraData.getVote(),
            initialExtraData.getRound(),
            initialExtraData.getValidators());

    final BlockHeader sealedHeader =
        BlockHeaderBuilder.fromHeader(initialHeader)
            .extraData(sealedExtraData.encode())
            .blockHeaderFunctions(IbftBlockHeaderFunctions.forOnChainBlock())
            .buildBlockHeader();

    return new Block(sealedHeader, block.getBody());
  }

  public static Optional<PreparedCertificate> findLatestPreparedCertificate(
      final Collection<SignedData<RoundChangePayload>> msgs) {

    Optional<PreparedCertificate> result = Optional.empty();

    for (SignedData<RoundChangePayload> roundChangeMsg : msgs) {
      final RoundChangePayload payload = roundChangeMsg.getPayload();
      if (payload.getPreparedCertificate().isPresent()) {
        if (!result.isPresent()) {
          result = payload.getPreparedCertificate();
        } else {
          final PreparedCertificate currentLatest = result.get();
          final PreparedCertificate nextCert = payload.getPreparedCertificate().get();

          if (currentLatest.getProposalPayload().getPayload().getRoundIdentifier().getRoundNumber()
              < nextCert.getProposalPayload().getPayload().getRoundIdentifier().getRoundNumber()) {
            result = Optional.of(nextCert);
          }
        }
      }
    }
    return result;
  }
}
