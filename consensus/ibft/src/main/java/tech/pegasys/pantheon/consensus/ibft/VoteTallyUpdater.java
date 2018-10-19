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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

public interface VoteTallyUpdater {
  /**
   * Create a new VoteTally based on the current blockchain state.
   *
   * @param blockchain the blockchain to load the current state from
   * @return a VoteTally reflecting the state of the blockchain head
   */
  VoteTally buildVoteTallyFromBlockchain(final Blockchain blockchain);

  /**
   * Update the vote tally to reflect changes caused by appending a new block to the chain.
   *
   * @param header the header of the block being added
   * @param voteTally the vote tally to update
   */
  void updateForBlock(final BlockHeader header, final VoteTally voteTally);
}
