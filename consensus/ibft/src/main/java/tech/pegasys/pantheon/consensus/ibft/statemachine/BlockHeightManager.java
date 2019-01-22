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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

public interface BlockHeightManager {

  void start();

  void handleBlockTimerExpiry(ConsensusRoundIdentifier roundIdentifier);

  void roundExpired(RoundExpiry expire);

  void handleProposalPayload(SignedData<ProposalPayload> signedPayload);

  void handlePreparePayload(SignedData<PreparePayload> signedPayload);

  void handleCommitPayload(SignedData<CommitPayload> payload);

  void handleRoundChangePayload(SignedData<RoundChangePayload> signedPayload);

  void handleNewRoundPayload(SignedData<NewRoundPayload> signedPayload);

  long getChainHeight();

  BlockHeader getParentBlockHeader();
}
