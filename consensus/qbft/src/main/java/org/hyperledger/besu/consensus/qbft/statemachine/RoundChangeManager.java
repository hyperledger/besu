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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.validation.RoundChangeMessageValidator;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for handling all RoundChange messages received for a given block height
 * (theoretically, RoundChange messages for a older height should have been previously discarded,
 * and messages for a future round should have been buffered).
 *
 * <p>If enough RoundChange messages all targeting a given round are received (and this node is the
 * proposer for said round) - a proposal message is sent, and a new round should be started by the
 * controlling class.
 */
public class RoundChangeManager {

  public static class RoundChangeStatus {

    private final long quorum;

    // Store only 1 round change per round per validator
    @VisibleForTesting final Map<Address, RoundChange> receivedMessages = Maps.newLinkedHashMap();

    private boolean actioned = false;

    public RoundChangeStatus(final long quorum) {
      this.quorum = quorum;
    }

    public void addMessage(final RoundChange msg) {
      if (!actioned) {
        receivedMessages.putIfAbsent(msg.getAuthor(), msg);
      }
    }

    public boolean roundChangeReady() {
      return receivedMessages.size() >= quorum && !actioned;
    }

    public Collection<RoundChange> createRoundChangeCertificate() {
      if (roundChangeReady()) {
        actioned = true;
        return receivedMessages.values();
      } else {
        throw new IllegalStateException("Unable to create RoundChangeCertificate at this time.");
      }
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(RoundChangeManager.class);

  @VisibleForTesting
  final Map<ConsensusRoundIdentifier, RoundChangeStatus> roundChangeCache = Maps.newHashMap();

  private final long quorum;
  private final RoundChangeMessageValidator roundChangeMessageValidator;

  public RoundChangeManager(
      final long quorum, final RoundChangeMessageValidator roundChangeMessageValidator) {
    this.quorum = quorum;
    this.roundChangeMessageValidator = roundChangeMessageValidator;
  }

  /**
   * Adds the round message to this manager and return a certificate if it passes the threshold
   *
   * @param msg The signed round change message to add
   * @return Empty if the round change threshold hasn't been hit, otherwise a round change
   *     certificate
   */
  public Optional<Collection<RoundChange>> appendRoundChangeMessage(final RoundChange msg) {

    if (!isMessageValid(msg)) {
      LOG.info("RoundChange message was invalid.");
      return Optional.empty();
    }

    final RoundChangeStatus roundChangeStatus = storeRoundChangeMessage(msg);

    if (roundChangeStatus.roundChangeReady()) {
      return Optional.of(roundChangeStatus.createRoundChangeCertificate());
    }

    return Optional.empty();
  }

  private boolean isMessageValid(final RoundChange msg) {
    return roundChangeMessageValidator.validate(msg);
  }

  private RoundChangeStatus storeRoundChangeMessage(final RoundChange msg) {
    final ConsensusRoundIdentifier msgTargetRound = msg.getRoundIdentifier();

    final RoundChangeStatus roundChangeStatus =
        roundChangeCache.computeIfAbsent(msgTargetRound, ignored -> new RoundChangeStatus(quorum));

    roundChangeStatus.addMessage(msg);

    return roundChangeStatus;
  }

  /**
   * Clears old rounds from storage that have been superseded by a given round
   *
   * @param completedRoundIdentifier round identifier that has been identified as superseded
   */
  public void discardRoundsPriorTo(final ConsensusRoundIdentifier completedRoundIdentifier) {
    roundChangeCache.keySet().removeIf(k -> isAnEarlierRound(k, completedRoundIdentifier));
  }

  private boolean isAnEarlierRound(
      final ConsensusRoundIdentifier left, final ConsensusRoundIdentifier right) {
    return left.getRoundNumber() < right.getRoundNumber();
  }
}
