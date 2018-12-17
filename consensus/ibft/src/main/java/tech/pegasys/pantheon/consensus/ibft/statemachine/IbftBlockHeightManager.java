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

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;

/** This no-op version will be replaced with an implementation in another PR */
public class IbftBlockHeightManager {

  public void handleProposalMessage(final SignedData<ProposalPayload> proposalMsg) {}

  public void handlePrepareMessage(final SignedData<PreparePayload> prepareMsg) {}

  public void handleCommitMessage(final SignedData<CommitPayload> commitMsg) {}

  public void handleBlockTimerExpiry(final ConsensusRoundIdentifier roundIndentifier) {}

  public void handleRoundChangeMessage(final SignedData<RoundChangePayload> roundChangeMsg) {}

  public void handleNewRoundMessage(final SignedData<NewRoundPayload> newRoundMsg) {}

  public void start() {}

  public long getChainHeight() {
    return 0;
  }

  public void roundExpired(final RoundExpiry expired) {}
}
