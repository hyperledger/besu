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
package org.hyperledger.besu.consensus.qbft.support;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
Responsible for creating an environment in which integration testing can be conducted.

The test setup is an 'n' node network, one of which is the local node (i.e. the Unit Under Test).

There is some complexity with determining the which node is the proposer etc. THus necessitating
NetworkLayout and RoundSpecificNodeRoles concepts.
 */
public class TestContext {

  private final Map<Address, ValidatorPeer> remotePeers;
  private final MutableBlockchain blockchain;
  private final BftExecutors bftExecutors;
  private final BftEventHandler controller;
  private final BftFinalState finalState;
  private final EventMultiplexer eventMultiplexer;
  private final MessageFactory messageFactory;
  private final ValidatorProvider validatorProvider;
  private final BftExtraDataCodec bftExtraDataCodec;

  public TestContext(
      final Map<Address, ValidatorPeer> remotePeers,
      final MutableBlockchain blockchain,
      final BftExecutors bftExecutors,
      final BftEventHandler controller,
      final BftFinalState finalState,
      final EventMultiplexer eventMultiplexer,
      final MessageFactory messageFactory,
      final ValidatorProvider validatorProvider,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.remotePeers = remotePeers;
    this.blockchain = blockchain;
    this.bftExecutors = bftExecutors;
    this.controller = controller;
    this.finalState = finalState;
    this.eventMultiplexer = eventMultiplexer;
    this.messageFactory = messageFactory;
    this.validatorProvider = validatorProvider;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  public void start() {
    bftExecutors.start();
    controller.start();
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public BftEventHandler getController() {
    return controller;
  }

  public EventMultiplexer getEventMultiplexer() {
    return eventMultiplexer;
  }

  public MessageFactory getLocalNodeMessageFactory() {
    return messageFactory;
  }

  public Block createBlockForProposalFromChainHead(final long timestamp) {
    return createBlockForProposalFromChainHead(timestamp, finalState.getLocalAddress());
  }

  public Block createBlockForProposal(
      final BlockHeader parent, final long timestamp, final Address proposer) {
    final Block block =
        finalState.getBlockCreatorFactory().create(parent, 0).createBlock(timestamp);

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(block.getHeader());
    headerBuilder
        .coinbase(proposer)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    return new Block(newHeader, block.getBody());
  }

  public Block createBlockForProposalFromChainHead(final long timestamp, final Address proposer) {
    // this implies that EVERY block will have this node as the proposer :/
    return createBlockForProposal(blockchain.getChainHeadHeader(), timestamp, proposer);
  }

  public RoundSpecificPeers roundSpecificPeers(final ConsensusRoundIdentifier roundId) {
    // This will return NULL if the LOCAL node is the proposer for the specified round
    final Address proposerAddress = finalState.getProposerForRound(roundId);
    final ValidatorPeer proposer = remotePeers.getOrDefault(proposerAddress, null);

    final List<ValidatorPeer> nonProposers = new ArrayList<>(remotePeers.values());
    nonProposers.remove(proposer);

    return new RoundSpecificPeers(proposer, remotePeers.values(), nonProposers, bftExtraDataCodec);
  }

  public NodeParams getLocalNodeParams() {
    return new NodeParams(finalState.getLocalAddress(), finalState.getNodeKey());
  }

  public long getCurrentChainHeight() {
    return blockchain.getChainHeadBlockNumber();
  }

  public ValidatorProvider getValidatorProvider() {
    return validatorProvider;
  }
}
