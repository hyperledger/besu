/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.datatypes.Address;

/**
 * Responsible for determining which member of the validator pool should propose the next block
 * (i.e. send the Proposal message).*
 */
public interface ProposerSelector {
  /**
   * Determines which validator should be acting as the proposer for a given sequence/round.
   *
   * @param roundIdentifier Identifies the chain height and proposal attempt number.
   * @return The address of the node which is to propose a block for the provided Round.
   */
  Address selectProposerForRound(ConsensusRoundIdentifier roundIdentifier);
}
