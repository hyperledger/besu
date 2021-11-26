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

import java.util.Collection;
import java.util.Optional;

public interface ValidatorProvider {

  Collection<Address> getValidatorsAtHead();

  Collection<Address> getValidatorsAfterBlock(BlockHeader header);

  Collection<Address> getValidatorsForBlock(BlockHeader header);

  Optional<VoteProvider> getVoteProviderAtHead();

  /*
   * ForkingValidatorProvider has a specific implementation but we don't want the client code to
   * know it's using a ForkingValidatorProvider. ForkingValidatorProvider's voteProvider can be
   * different per block. Other ValidatorProviders yield the same voteProvider at every block.
   */
  default Optional<VoteProvider> getVoteProviderAfterBlock(final BlockHeader header) {
    return getVoteProviderAtHead();
  }
}
