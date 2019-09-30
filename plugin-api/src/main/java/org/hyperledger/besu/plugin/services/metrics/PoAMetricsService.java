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

import java.util.Collection;

/** Provides relevant data for producing metrics on the status of a Proof of Authority (PoA) node. */
public interface PoAMetricsService {

  /**
   * Returns a collection containing the addresses of the validators for the most recently processed
   * block.
   *
   * @return A collection of block validator addresses.
   */
  Collection<Address> getValidatorsForLatestBlock();

  /**
   * Returns the {@link Address} of the proposer of a specified block.
   *
   * @param header The {@link BlockHeader} to be queried.
   * @return The {@link Address} of the proposer of the block.
   */
  Address getProposerOfBlock(final BlockHeader header);
}
