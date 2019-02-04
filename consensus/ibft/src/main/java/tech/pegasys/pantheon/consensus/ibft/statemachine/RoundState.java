/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.prepareMessageCountForQuorum;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;

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
      if (validator.addSignedProposalPayload(msg)) {
        proposalMessage = Optional.of(msg);
        prepareMessages.removeIf(p -> !validator.validatePrepareMessage(p));
        commitMessages.removeIf(p -> !validator.validateCommitMessage(p));
        updateState();
        return true;
      }
    }

    return false;
  }

  public void addPrepareMessage(final Prepare msg) {
    if (!proposalMessage.isPresent() || validator.validatePrepareMessage(msg)) {
      prepareMessages.add(msg);
      LOG.debug("Round state added prepare message prepare={}", msg);
    }
    updateState();
  }

  public void addCommitMessage(final Commit msg) {
    if (!proposalMessage.isPresent() || validator.validateCommitMessage(msg)) {
      commitMessages.add(msg);
      LOG.debug("Round state added commit message commit={}", msg);
    }

    updateState();
  }

  private void updateState() {
    // NOTE: The quorum for Prepare messages is 1 less than the quorum size as the proposer
    // does not supply a prepare message
    final long prepareQuorum = prepareMessageCountForQuorum(quorum);
    prepared = (prepareMessages.size() >= prepareQuorum) && proposalMessage.isPresent();
    committed = (commitMessages.size() >= quorum) && proposalMessage.isPresent();
    LOG.debug(
        "Round state updated prepared={} committed={} prepareQuorum={} quorum={}",
        prepared,
        committed,
        prepareQuorum,
        quorum);
  }

  public Optional<Block> getProposedBlock() {
    return proposalMessage.map(p -> p.getSignedPayload().getPayload().getBlock());
  }

  public boolean isPrepared() {
    return prepared;
  }

  public boolean isCommitted() {
    return committed;
  }

  public Collection<Signature> getCommitSeals() {
    return commitMessages
        .stream()
        .map(cp -> cp.getSignedPayload().getPayload().getCommitSeal())
        .collect(Collectors.toList());
  }

  public Optional<TerminatedRoundArtefacts> constructTerminatedRoundArtefacts() {
    if (isPrepared()) {
      return Optional.of(new TerminatedRoundArtefacts(proposalMessage.get(), prepareMessages));
    }
    return Optional.empty();
  }
}
