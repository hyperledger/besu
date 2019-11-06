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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;
import java.util.Map;

public class ForkingVoteTallyCache extends VoteTallyCache {

  private final Map<Long, List<Address>> overridenValidators;

  public ForkingVoteTallyCache(
      final Blockchain blockchain,
      final VoteTallyUpdater voteTallyUpdater,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final Map<Long, List<Address>> overridenValidators) {
    super(blockchain, voteTallyUpdater, epochManager, blockInterface);
    checkNotNull(overridenValidators);
    this.overridenValidators = overridenValidators;
  }

  @Override
  protected VoteTally getValidatorsAfter(final BlockHeader header) {
    final long actualBlockNumber = header.getNumber() + 1L;
    if (overridenValidators.containsKey(actualBlockNumber)) {
      return new VoteTally(overridenValidators.get(actualBlockNumber));
    }

    return super.getValidatorsAfter(header);
  }
}
