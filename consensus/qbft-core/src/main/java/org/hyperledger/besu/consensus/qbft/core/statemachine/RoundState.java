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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidator;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Round state defines how a round will operate. */
// Data items used to define how a round will operate
public class RoundState {
  private static final Logger LOG = LoggerFactory.getLogger(RoundState.class);

  private final ConsensusRoundIdentifier roundIdentifier;
  private final MessageValidator validator;
  private final long quorum;

  private Optional<Proposal> proposalMessage = Optional.empty();

  // Must track the actual Prepare message, not just the sender, as these may need to be reused
  // to send out in a PrepareCertificate.
  private final Set<Prepare> prepareMessages = Sets.newLinkedHashSet();
  private final Set<Commit> commitMessages = Sets.newLinkedHashSet();

  private boolean prepared = false;
  private boolean committed = false;

  /**
   * Instantiates a new Round state.
   *
   * @param roundIdentifier the round identifier
   * @param quorum the quorum
   * @param validator the validator
   */
  public RoundState(
      final ConsensusRoundIdentifier roundIdentifier,
      final int quorum,
      final MessageValidator validator) {
    this.roundIdentifier = roundIdentifier;
    this.quorum = quorum;
    this.validator = validator;
  }

  /**
   * Gets round identifier.
   *
   * @return the round identifier
   */
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundIdentifier;
  }

  /**
   * Sets proposed block.
   *
   * @param msg the Proposal payload msg
   * @return the proposed block
   */
  public boolean setProposedBlock(final Proposal msg) {

    if (!proposalMessage.isPresent()) {
      if (validator.validateProposal(msg)) {
        proposalMessage = Optional.of(msg);
        prepareMessages.removeIf(p -> !validator.validatePrepare(p));
        commitMessages.removeIf(p -> !validator.validateCommit(p));
        updateState();
        return true;
      }
    }

    return false;
  }

  /**
   * Add prepare message.
   *
   * @param msg the msg
   */
  public void addPrepareMessage(final Prepare msg) {
    if (!proposalMessage.isPresent() || validator.validatePrepare(msg)) {
      prepareMessages.add(msg);
      LOG.trace("Round state added prepare message prepare={}", msg);
    }
    updateState();
  }

  /**
   * Add commit message.
   *
   * @param msg the msg
   */
  public void addCommitMessage(final Commit msg) {
    if (!proposalMessage.isPresent() || validator.validateCommit(msg)) {
      commitMessages.add(msg);
      LOG.trace("Round state added commit message commit={}", msg);
    }

    updateState();
  }

  private void updateState() {
    prepared = (prepareMessages.size() >= quorum) && proposalMessage.isPresent();
    committed = (commitMessages.size() >= quorum) && proposalMessage.isPresent();
    LOG.trace(
        "Round state updated prepared={} committed={} preparedQuorum={}/{} committedQuorum={}/{}",
        prepared,
        committed,
        prepareMessages.size(),
        quorum,
        commitMessages.size(),
        quorum);
  }

  /**
   * Gets proposed block.
   *
   * @return the proposed block
   */
  public Optional<Block> getProposedBlock() {
    return proposalMessage.map(p -> p.getSignedPayload().getPayload().getProposedBlock());
  }

  /**
   * Is prepared.
   *
   * @return the boolean
   */
  public boolean isPrepared() {
    return prepared;
  }

  /**
   * Is committed.
   *
   * @return the boolean
   */
  public boolean isCommitted() {
    return committed;
  }

  /**
   * Gets commit seals.
   *
   * @return the commit seals
   */
  public Collection<SECPSignature> getCommitSeals() {
    return commitMessages.stream()
        .map(cp -> cp.getSignedPayload().getPayload().getCommitSeal())
        .collect(Collectors.toList());
  }

  /**
   * Construct prepared certificate.
   *
   * @return the optional prepared certificate
   */
  public Optional<PreparedCertificate> constructPreparedCertificate() {
    if (isPrepared()) {
      return Optional.of(
          new PreparedCertificate(
              proposalMessage.get().getSignedPayload().getPayload().getProposedBlock(),
              prepareMessages.stream().map(Prepare::getSignedPayload).collect(Collectors.toList()),
              roundIdentifier.getRoundNumber()));
    }
    return Optional.empty();
  }
}
