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
package org.hyperledger.besu.consensus.qbft.payload;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public class RoundChangeMetadata {

  private final Optional<Block> block;
  private final List<SignedData<RoundChangePayload>> roundChangePayloads;
  private final List<SignedData<PreparePayload>> prepares;

  public RoundChangeMetadata(
      final Optional<Block> block,
      final List<SignedData<RoundChangePayload>> roundChangePayloads,
      final List<SignedData<PreparePayload>> prepares) {
    this.block = block;
    this.roundChangePayloads = roundChangePayloads;
    this.prepares = prepares;
  }

  public List<SignedData<PreparePayload>> getPrepares() {
    return prepares;
  }

  public List<SignedData<RoundChangePayload>> getRoundChangePayloads() {
    return roundChangePayloads;
  }

  public Optional<Block> getBlock() {
    return block;
  }

  public static RoundChangeMetadata create(final Collection<RoundChange> roundChanges) {

    final Comparator<RoundChange> preparedRoundComparator =
        (o1, o2) -> {
          if (o1.getPreparedRound().isEmpty()) {
            return -1;
          }
          if (o2.getPreparedRound().isEmpty()) {
            return 1;
          }
          return o1.getPreparedRound().get().compareTo(o2.getPreparedRound().get());
        };

    final Optional<RoundChange> roundChangeWithNewestPrepare =
        roundChanges.stream().max(preparedRoundComparator);

    final Optional<Block> proposedBlock;
    final List<SignedData<PreparePayload>> prepares = Lists.newArrayList();
    if (roundChangeWithNewestPrepare.isPresent()) {
      proposedBlock = roundChangeWithNewestPrepare.get().getProposedBlock();
      prepares.addAll(roundChangeWithNewestPrepare.get().getPrepares());
    } else {
      proposedBlock = Optional.empty();
    }

    final List<SignedData<RoundChangePayload>> payloads =
        roundChanges.stream().map(RoundChange::getSignedPayload).collect(Collectors.toList());

    return new RoundChangeMetadata(proposedBlock, payloads, prepares);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RoundChangeMetadata.class.getSimpleName() + "[", "]")
        .add("roundChangePayloads=" + roundChangePayloads)
        .toString();
  }
}
