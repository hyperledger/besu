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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugin.services.metrics;

import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BesuService;

import java.util.Collection;

/**
 * Provides relevant data for producing metrics on the status of a Proof of Authority (PoA) node.
 *
 * @deprecated This interface has been replaced by {@link
 *     org.hyperledger.besu.plugin.services.query.PoaQueryService}
 */
@Deprecated
public interface PoAMetricsService extends BesuService {

  /**
   * Retrieves the validators who have signed the latest block from the canonical chain.
   *
   * @return Identities of the validators who formed quorum on the latest block.
   */
  Collection<Address> getValidatorsForLatestBlock();

  /**
   * Retrieves the {@link Address} for the proposer of a block on the canonical chain.
   *
   * @param header The {@link BlockHeader} for which the proposer will be found.
   * @return The identity of the proposer for the given block.
   */
  Address getProposerOfBlock(final BlockHeader header);
}
