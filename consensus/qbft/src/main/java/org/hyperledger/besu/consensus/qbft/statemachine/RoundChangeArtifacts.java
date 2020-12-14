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
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangeCertificate;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RoundChangeArtifacts {

  private final Optional<Block> block;
  private final Collection<RoundChange> roundChanges;
  private final Collection<SignedData<PreparePayload>> prepares;

  public RoundChangeArtifacts(
      final Optional<Block> block,
      final Collection<RoundChange> roundChangePayloads,
      final Collection<SignedData<PreparePayload>> prepares) {
    this.block = block;
    this.roundChanges = roundChangePayloads;
    this.prepares = prepares;
  }

  public Optional<Block> getBlock() {
    return block;
  }

  public RoundChangeCertificate getRoundChangeCertificate() {
    final List<SignedData<RoundChangePayload>> payloads =
        roundChanges.stream().map(RoundChange::getSignedPayload).collect(Collectors.toList());

    return new RoundChangeCertificate(payloads, prepares);
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
        roundChanges.stream().max(preparedRoundComparator);

    final Optional<Block> proposedBlock;
    final List<SignedData<PreparePayload>> prepares = Collections.emptyList();
    if (roundChangeWithNewestPrepare.isPresent()) {
      proposedBlock = roundChangeWithNewestPrepare.get().getProposedBlock();
      prepares.addAll(roundChangeWithNewestPrepare.get().getPrepares());
    } else {
      proposedBlock = Optional.empty();
    }

    return new RoundChangeArtifacts(proposedBlock, roundChanges, prepares);
  }
}
