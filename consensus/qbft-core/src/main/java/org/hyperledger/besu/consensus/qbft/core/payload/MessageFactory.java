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
package org.hyperledger.besu.consensus.qbft.core.payload;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.statemachine.PreparedCertificate;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** The Message factory. */
public class MessageFactory {

  private final NodeKey nodeKey;

  /**
   * Instantiates a new Message factory.
   *
   * @param nodeKey the node key
   */
  public MessageFactory(final NodeKey nodeKey) {
    this.nodeKey = nodeKey;
  }

  /**
   * Create proposal.
   *
   * @param roundIdentifier the round identifier
   * @param block the block
   * @param roundChanges the round changes
   * @param prepares the prepares
   * @return the proposal
   */
  public Proposal createProposal(
      final ConsensusRoundIdentifier roundIdentifier,
      final Block block,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares) {

    final ProposalPayload payload = new ProposalPayload(roundIdentifier, block);

    return new Proposal(createSignedMessage(payload), roundChanges, prepares);
  }

  /**
   * Create Prepare payload.
   *
   * @param roundIdentifier the round identifier
   * @param digest the digest
   * @return the prepare
   */
  public Prepare createPrepare(final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {
    final PreparePayload payload = new PreparePayload(roundIdentifier, digest);
    return new Prepare(createSignedMessage(payload));
  }

  /**
   * Create commit payload.
   *
   * @param roundIdentifier the round identifier
   * @param digest the digest
   * @param commitSeal the commit seal
   * @return the commit
   */
  public Commit createCommit(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final SECPSignature commitSeal) {
    final CommitPayload payload = new CommitPayload(roundIdentifier, digest, commitSeal);
    return new Commit(createSignedMessage(payload));
  }

  /**
   * Create round change payload.
   *
   * @param roundIdentifier the round identifier
   * @param preparedRoundData the prepared round data
   * @return the round change
   */
  public RoundChange createRoundChange(
      final ConsensusRoundIdentifier roundIdentifier,
      final Optional<PreparedCertificate> preparedRoundData) {

    final RoundChangePayload payload;
    if (preparedRoundData.isPresent()) {

      final Block preparedBlock = preparedRoundData.get().getBlock();
      payload =
          new RoundChangePayload(
              roundIdentifier,
              Optional.of(
                  new PreparedRoundMetadata(
                      preparedBlock.getHash(), preparedRoundData.get().getRound())));

      return new RoundChange(
          createSignedMessage(payload),
          Optional.of(preparedBlock),
          preparedRoundData.get().getPrepares());

    } else {
      payload = new RoundChangePayload(roundIdentifier, Optional.empty());
      return new RoundChange(
          createSignedMessage(payload), Optional.empty(), Collections.emptyList());
    }
  }

  private <M extends Payload> SignedData<M> createSignedMessage(final M payload) {
    final SECPSignature signature = nodeKey.sign(payload.hashForSignature());
    return SignedData.create(payload, signature);
  }
}
