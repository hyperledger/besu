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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** The Round change artifacts. */
public class RoundChangeArtifacts {

  private final List<SignedData<RoundChangePayload>> roundChanges;
  private final Optional<PreparedCertificate> bestPreparedPeer;

  /**
   * Instantiates a new Round change artifacts.
   *
   * @param roundChanges the round changes
   * @param bestPreparedPeer the best prepared peer
   */
  public RoundChangeArtifacts(
      final List<SignedData<RoundChangePayload>> roundChanges,
      final Optional<PreparedCertificate> bestPreparedPeer) {
    this.roundChanges = roundChanges;
    this.bestPreparedPeer = bestPreparedPeer;
  }

  /**
   * Gets best prepared peer certificate.
   *
   * @return the best prepared peer
   */
  public Optional<PreparedCertificate> getBestPreparedPeer() {
    return bestPreparedPeer;
  }

  /**
   * Gets round changes.
   *
   * @return the round changes
   */
  public List<SignedData<RoundChangePayload>> getRoundChanges() {
    return roundChanges;
  }

  /**
   * Create round change artifacts.
   *
   * @param roundChanges the round changes
   * @return the round change artifacts
   */
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
