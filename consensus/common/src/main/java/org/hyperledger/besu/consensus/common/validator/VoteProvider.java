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
package org.hyperledger.besu.consensus.common.validator;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Map;
import java.util.Optional;

/** The interface Vote provider. */
public interface VoteProvider {

  /**
   * Gets vote after block.
   *
   * @param header the header
   * @param localAddress the local address
   * @return the vote after block
   */
  Optional<ValidatorVote> getVoteAfterBlock(final BlockHeader header, final Address localAddress);

  /**
   * Auth vote.
   *
   * @param address the address
   */
  void authVote(final Address address);

  /**
   * Drop vote.
   *
   * @param address the address
   */
  void dropVote(final Address address);

  /**
   * Discard vote.
   *
   * @param address the address
   */
  void discardVote(final Address address);

  /**
   * Gets proposals.
   *
   * @return the proposals
   */
  Map<Address, VoteType> getProposals();
}
