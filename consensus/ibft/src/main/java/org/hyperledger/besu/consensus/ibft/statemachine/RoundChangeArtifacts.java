/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.ibft.statemachine;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RoundChangeArtifacts {

  private final Optional<Block> block;
  private final List<SignedData<RoundChangePayload>> roundChangePayloads;

  public RoundChangeArtifacts(
      final Optional<Block> block, final List<SignedData<RoundChangePayload>> roundChangePayloads) {
    this.block = block;
    this.roundChangePayloads = roundChangePayloads;
  }

  public Optional<Block> getBlock() {
    return block;
  }

  public RoundChangeCertificate getRoundChangeCertificate() {
    return new RoundChangeCertificate(roundChangePayloads);
  }

  public static RoundChangeArtifacts create(final Collection<RoundChange> roundChanges) {

    final Comparator<RoundChange> preparedRoundComparator =
        (o1, o2) -> {
          if (!o1.getPreparedCertificateRound().isPresent()) {
            return -1;
          }
          if (!o2.getPreparedCertificateRound().isPresent()) {
            return 1;
          }
          return o1.getPreparedCertificateRound()
              .get()
              .compareTo(o2.getPreparedCertificateRound().get());
        };

    final List<SignedData<RoundChangePayload>> payloads =
        roundChanges.stream().map(RoundChange::getSignedPayload).collect(Collectors.toList());

    final Optional<RoundChange> roundChangeWithNewestPrepare =
        roundChanges.stream().max(preparedRoundComparator);

    final Optional<Block> proposedBlock =
        roundChangeWithNewestPrepare.flatMap(RoundChange::getProposedBlock);
    return new RoundChangeArtifacts(proposedBlock, payloads);
  }
}
