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

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidator;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Data items used to define how a round will operate
public class RoundState {
  private static final Logger LOG = LogManager.getLogger();

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

  public RoundState(
      final ConsensusRoundIdentifier roundIdentifier,
      final int quorum,
      final MessageValidator validator) {
    this.roundIdentifier = roundIdentifier;
    this.quorum = quorum;
    this.validator = validator;
  }

  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundIdentifier;
  }

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

  public void addPrepareMessage(final Prepare msg) {
    if (!proposalMessage.isPresent() || validator.validatePrepare(msg)) {
      prepareMessages.add(msg);
      LOG.trace("Round state added prepare message prepare={}", msg);
    }
    updateState();
  }

  public void addCommitMessage(final Commit msg) {
    if (!proposalMessage.isPresent() || validator.validateCommit(msg)) {
      commitMessages.add(msg);
      LOG.trace("Round state added commit message commit={}", msg);
    }

    updateState();
  }

  private void updateState() {
    // NOTE: The quorum for Prepare messages is 1 less than the quorum size as the proposer
    // does not supply a prepare message
    final long prepareQuorum = BftHelpers.prepareMessageCountForQuorum(quorum);
    prepared = (prepareMessages.size() >= prepareQuorum) && proposalMessage.isPresent();
    committed = (commitMessages.size() >= quorum) && proposalMessage.isPresent();
    LOG.trace(
        "Round state updated prepared={} committed={} preparedQuorum={}/{} committedQuorum={}/{}",
        prepared,
        committed,
        prepareMessages.size(),
        prepareQuorum,
        commitMessages.size(),
        quorum);
  }

  public Optional<Block> getProposedBlock() {
    return proposalMessage.map(Proposal::getBlock);
  }

  public boolean isPrepared() {
    return prepared;
  }

  public boolean isCommitted() {
    return committed;
  }

  public Collection<SECPSignature> getCommitSeals() {
    return commitMessages.stream()
        .map(cp -> cp.getSignedPayload().getPayload().getCommitSeal())
        .collect(Collectors.toList());
  }

  public Optional<PreparedRoundArtifacts> constructPreparedRoundArtifacts() {
    if (isPrepared()) {
      return Optional.of(new PreparedRoundArtifacts(proposalMessage.get(), prepareMessages));
    }
    return Optional.empty();
  }
}
