/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.support;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.EventMultiplexer;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftEvents;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

// Each "inject" function returns the SignedPayload representation of the transmitted message.
public class ValidatorPeer {

  private final Address nodeAddress;
  private final KeyPair nodeKeys;
  private final MessageFactory messageFactory;
  private final PeerConnection peerConnection;
  private final List<MessageData> receivedMessages = Lists.newArrayList();

  private final EventMultiplexer localEventMultiplexer;

  public ValidatorPeer(
      final NodeParams nodeParams,
      final MessageFactory messageFactory,
      final EventMultiplexer localEventMultiplexer) {
    this.nodeKeys = nodeParams.getNodeKeyPair();
    this.nodeAddress = nodeParams.getAddress();
    this.messageFactory = messageFactory;
    final BytesValue nodeId = nodeKeys.getPublicKey().getEncodedBytes();
    this.peerConnection = new StubbedPeerConnection(nodeId);
    this.localEventMultiplexer = localEventMultiplexer;
  }

  public Address getNodeAddress() {
    return nodeAddress;
  }

  public KeyPair getNodeKeys() {
    return nodeKeys;
  }

  public SignedData<ProposalPayload> injectProposal(
      final ConsensusRoundIdentifier rId, final Block block) {
    final SignedData<ProposalPayload> payload =
        messageFactory.createSignedProposalPayload(rId, block);

    injectMessage(ProposalMessageData.create(new Proposal(payload)));
    return payload;
  }

  public SignedData<PreparePayload> injectPrepare(
      final ConsensusRoundIdentifier rId, final Hash digest) {
    final SignedData<PreparePayload> payload =
        messageFactory.createSignedPreparePayload(rId, digest);
    injectMessage(PrepareMessageData.create(new Prepare(payload)));
    return payload;
  }

  public Signature getBlockSignature(final Hash digest) {
    return SECP256K1.sign(digest, nodeKeys);
  }

  public SignedData<CommitPayload> injectCommit(
      final ConsensusRoundIdentifier rId, final Hash digest) {
    final Signature commitSeal = SECP256K1.sign(digest, nodeKeys);

    return injectCommit(rId, digest, commitSeal);
  }

  public SignedData<CommitPayload> injectCommit(
      final ConsensusRoundIdentifier rId, final Hash digest, final Signature commitSeal) {
    final SignedData<CommitPayload> payload =
        messageFactory.createSignedCommitPayload(rId, digest, commitSeal);
    injectMessage(CommitMessageData.create(new Commit(payload)));
    return payload;
  }

  public SignedData<NewRoundPayload> injectNewRound(
      final ConsensusRoundIdentifier rId,
      final RoundChangeCertificate roundChangeCertificate,
      final SignedData<ProposalPayload> proposalPayload) {

    final SignedData<NewRoundPayload> payload =
        messageFactory.createSignedNewRoundPayload(rId, roundChangeCertificate, proposalPayload);
    injectMessage(NewRoundMessageData.create(new NewRound(payload)));
    return payload;
  }

  public SignedData<RoundChangePayload> injectRoundChange(
      final ConsensusRoundIdentifier rId, final Optional<PreparedCertificate> preparedCertificate) {
    final SignedData<RoundChangePayload> payload =
        messageFactory.createSignedRoundChangePayload(rId, preparedCertificate);
    injectMessage(RoundChangeMessageData.create(new RoundChange(payload)));
    return payload;
  }

  public void handleReceivedMessage(final MessageData message) {
    receivedMessages.add(message);
  }

  public List<MessageData> getReceivedMessages() {
    return Collections.unmodifiableList(receivedMessages);
  }

  public void clearReceivedMessages() {
    receivedMessages.clear();
  }

  public void injectMessage(final MessageData msgData) {
    final DefaultMessage message = new DefaultMessage(peerConnection, msgData);
    localEventMultiplexer.handleIbftEvent(IbftEvents.fromMessage(message));
  }

  public MessageFactory getMessageFactory() {
    return messageFactory;
  }

  public KeyPair getNodeKeyPair() {
    return nodeKeys;
  }
}
