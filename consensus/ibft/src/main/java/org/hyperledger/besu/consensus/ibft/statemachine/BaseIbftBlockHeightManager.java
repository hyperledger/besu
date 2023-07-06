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

import org.hyperledger.besu.consensus.common.bft.statemachine.BaseBlockHeightManager;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;

/** The interface Base ibft block height manager. */
public interface BaseIbftBlockHeightManager extends BaseBlockHeightManager {

  /**
   * Handle proposal payload.
   *
   * @param proposal the proposal
   */
  void handleProposalPayload(Proposal proposal);

  /**
   * Handle prepare payload.
   *
   * @param prepare the prepare
   */
  void handlePreparePayload(Prepare prepare);

  /**
   * Handle commit payload.
   *
   * @param commit the commit
   */
  void handleCommitPayload(Commit commit);

  /**
   * Handle round change payload.
   *
   * @param roundChange the round change
   */
  void handleRoundChangePayload(RoundChange roundChange);
}
