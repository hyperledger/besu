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
package org.hyperledger.besu.consensus.qbft.statemachine;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RoundChangeArtifacts {

  private final List<SignedData<RoundChangePayload>> roundChanges;
  private final Optional<PreparedCertificate> bestPreparedPeer;

  public RoundChangeArtifacts(
      final List<SignedData<RoundChangePayload>> roundChanges,
      final Optional<PreparedCertificate> bestPreparedPeer) {
    this.roundChanges = roundChanges;
    this.bestPreparedPeer = bestPreparedPeer;
  }

  public Optional<PreparedCertificate> getBestPreparedPeer() {
    return bestPreparedPeer;
  }

  public List<SignedData<RoundChangePayload>> getRoundChanges() {
    return roundChanges;
  }

  public static RoundChangeArtifacts create(final Collection<RoundChange> roundChanges) {

    final Comparator<RoundChange> preparedRoundComparator =
        (o1, o2) -> {
          if (o1.getPreparedRoundMetadata().isEmpty()) {
            return -1;
          }
          if (o2.getPreparedRoundMetadata().isEmpty()) {
            return 1;
          }

          int o1Round = o1.getPreparedRoundMetadata().get().getPreparedRound();
          int o2Round = o2.getPreparedRoundMetadata().get().getPreparedRound();

          return Integer.compare(o1Round, o2Round);
        };

    final Optional<RoundChange> roundChangeWithNewestPrepare =
        roundChanges.stream()
            .max(preparedRoundComparator)
            .flatMap(rc -> rc.getProposedBlock().map(pb -> rc));

    final Optional<PreparedCertificate> prepCert =
        roundChangeWithNewestPrepare.map(
            roundChange ->
                new PreparedCertificate(
                    roundChange.getProposedBlock().get(),
                    roundChange.getPrepares(),
                    roundChange.getPreparedRound().get()));

    return new RoundChangeArtifacts(
        roundChanges.stream().map(RoundChange::getSignedPayload).collect(Collectors.toList()),
        prepCert);
  }
}
