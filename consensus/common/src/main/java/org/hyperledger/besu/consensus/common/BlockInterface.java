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
package org.hyperledger.besu.consensus.common;

import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;

/** The interface Block interface. */
public interface BlockInterface {

  /**
   * Gets proposer of block.
   *
   * @param header the header
   * @return the proposer of block
   */
  Address getProposerOfBlock(final org.hyperledger.besu.ethereum.core.BlockHeader header);

  /**
   * Gets proposer of block.
   *
   * @param header the header
   * @return the proposer of block
   */
  Address getProposerOfBlock(final org.hyperledger.besu.plugin.data.BlockHeader header);

  /**
   * Extract vote from header optional.
   *
   * @param header the header
   * @return the optional
   */
  Optional<ValidatorVote> extractVoteFromHeader(final BlockHeader header);

  /**
   * Validators in block collection.
   *
   * @param header the header
   * @return the collection
   */
  Collection<Address> validatorsInBlock(final BlockHeader header);
}
