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
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

// Data items used to define how a round will operate
public class RoundState {

  private final ConsensusRoundIdentifier roundIdentifier;
  private final MessageValidator validator;
  private final long quorum;

  private Optional<SignedData<ProposalPayload>> proposalMessage = Optional.empty();

  // Must track the actual Prepare message, not just the sender, as these may need to be reused
  // to send out in a PrepareCertificate.
  private final Set<SignedData<PreparePayload>> preparePayloads = Sets.newLinkedHashSet();
  private final Set<SignedData<CommitPayload>> commitPayloads = Sets.newLinkedHashSet();

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

  public boolean setProposedBlock(final SignedData<ProposalPayload> msg) {

    if (!proposalMessage.isPresent()) {
      if (validator.addSignedProposalPayload(msg)) {
        proposalMessage = Optional.of(msg);
        preparePayloads.removeIf(p -> !validator.validatePrepareMessage(p));
        commitPayloads.removeIf(p -> !validator.validateCommmitMessage(p));
        updateState();
        return true;
      }
    }

    return false;
  }

  public void addPrepareMessage(final SignedData<PreparePayload> msg) {
    if (!proposalMessage.isPresent() || validator.validatePrepareMessage(msg)) {
      preparePayloads.add(msg);
    }
    updateState();
  }

  public void addCommitMessage(final SignedData<CommitPayload> msg) {
    if (!proposalMessage.isPresent() || validator.validateCommmitMessage(msg)) {
      commitPayloads.add(msg);
    }

    updateState();
  }

  private void updateState() {
    // NOTE: The quorum for Prepare messages is 1 less than the quorum size as the proposer
    // does not supply a prepare message
    prepared =
        (preparePayloads.size() >= prepareMessageCountForQuorum(quorum))
            && proposalMessage.isPresent();
    committed = (commitPayloads.size() >= quorum) && proposalMessage.isPresent();
  }

  public Optional<Block> getProposedBlock() {
    return proposalMessage.map(p -> p.getPayload().getBlock());
  }

  public boolean isPrepared() {
    return prepared;
  }

  public boolean isCommitted() {
    return committed;
  }

  public Collection<Signature> getCommitSeals() {
    return commitPayloads
        .stream()
        .map(cp -> cp.getPayload().getCommitSeal())
        .collect(Collectors.toList());
  }

  public Optional<PreparedCertificate> constructPreparedCertificate() {
    if (isPrepared()) {
      return Optional.of(new PreparedCertificate(proposalMessage.get(), preparePayloads));
    }
    return Optional.empty();
  }
}
