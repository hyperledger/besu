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
package org.hyperledger.besu.consensus.qbft.blockcreation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBaseProposerSelector;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QbftProposerSelector implements BftBaseProposerSelector {

  private static final Logger LOG = LogManager.getLogger();

  private final VoteTallyCache voteTallyCache;
  private final Blockchain blockchain;

  public QbftProposerSelector(final Blockchain blockchain, final VoteTallyCache voteTallyCache) {
    checkNotNull(voteTallyCache);
    checkNotNull(blockchain);
    this.blockchain = blockchain;
    this.voteTallyCache = voteTallyCache;
  }

  @Override
  public Address selectProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    checkArgument(roundIdentifier.getRoundNumber() >= 0);
    checkArgument(roundIdentifier.getSequenceNumber() > 0);

    final long prevBlockNumber = roundIdentifier.getSequenceNumber() - 1;
    final Optional<BlockHeader> maybeParentHeader = blockchain.getBlockHeader(prevBlockNumber);
    if (maybeParentHeader.isEmpty()) {
      LOG.trace("Unable to determine proposer for requested block {}", prevBlockNumber);
      throw new RuntimeException("Unable to determine past proposer");
    }

    final BlockHeader blockHeader = maybeParentHeader.get();
    final List<Address> validatorsForHeight =
        new ArrayList<>(voteTallyCache.getVoteTallyAfterBlock(blockHeader).getValidators());

    final BigInteger validatorCount = BigInteger.valueOf(validatorsForHeight.size());

    final BigInteger proposerIndex =
        BigInteger.valueOf(roundIdentifier.getRoundNumber() + roundIdentifier.getSequenceNumber())
            .mod(validatorCount);

    return validatorsForHeight.get(proposerIndex.intValue());
  }
}
