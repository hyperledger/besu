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

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;
import java.util.Optional;

/** The interface Validator provider. */
public interface ValidatorProvider {

  /**
   * Gets validators at head.
   *
   * @return the validators at head
   */
  Collection<Address> getValidatorsAtHead();

  /**
   * Gets validators after block.
   *
   * @param header the header
   * @return the validators after block
   */
  Collection<Address> getValidatorsAfterBlock(BlockHeader header);

  /**
   * Gets validators for block.
   *
   * @param header the header
   * @return the validators for block
   */
  Collection<Address> getValidatorsForBlock(BlockHeader header);

  /**
   * Gets vote provider at head.
   *
   * @return the vote provider at head
   */
  Optional<VoteProvider> getVoteProviderAtHead();

  /**
   * Gets vote provider after block.
   *
   * @param header the header
   * @return the vote provider after block
   */
  /*
   * ForkingValidatorProvider has a specific implementation but we don't want the client code to
   * know it's using a ForkingValidatorProvider. ForkingValidatorProvider's voteProvider can be
   * different per block. Other ValidatorProviders yield the same voteProvider at every block.
   */
  default Optional<VoteProvider> getVoteProviderAfterBlock(final BlockHeader header) {
    return getVoteProviderAtHead();
  }

  /**
   * Determines if this node is a validator
   *
   * @param nodekey our node key
   * @return true if this node is a validator
   */
  default boolean nodeIsValidator(final NodeKey nodekey) {
    return this.getValidatorsAtHead().contains(Util.publicKeyToAddress(nodekey.getPublicKey()));
  }
}
