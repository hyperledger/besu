/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.core.statemachine.PreparedCertificate;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidationTestHelpers {

  public static PreparedCertificate createPreparedCertificate(
      final Block block,
      final ConsensusRoundIdentifier reportedRound,
      final QbftNode... preparedNodes) {

    return new PreparedCertificate(
        block,
        createPreparePayloads(reportedRound, block.getHash(), preparedNodes),
        reportedRound.getRoundNumber());
  }

  public static List<SignedData<PreparePayload>> createPreparePayloads(
      final ConsensusRoundIdentifier reportedRound,
      final Hash blockHash,
      final QbftNode... preparedNodes) {
    return Stream.of(preparedNodes)
        .map(p -> p.getMessageFactory().createPrepare(reportedRound, blockHash).getSignedPayload())
        .collect(Collectors.toList());
  }

  public static List<SignedData<RoundChangePayload>> createEmptyRoundChangePayloads(
      final ConsensusRoundIdentifier targetRound, final QbftNode... roundChangingNodes) {
    return Stream.of(roundChangingNodes)
        .map(
            r ->
                r.getMessageFactory()
                    .createRoundChange(targetRound, Optional.empty())
                    .getSignedPayload())
        .collect(Collectors.toList());
  }
}
