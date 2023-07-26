/*
 * Copyright contributors to Hyperledger Besu.
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

package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.util.List;

/**
 * A Bft-specific wrapper around a ProtocolSchedule that is allowed to look up ProtocolSpecs by
 * block number. Extending DefaultProtocolSchedule gives this class access to the protocolSpecs
 */
public class BftProtocolSchedule extends DefaultProtocolSchedule {

  /**
   * Construct from an existing DefaultProtocolSchedule
   *
   * @param protocolSchedule a blockNumber-supporting ProtocolSchedule
   */
  public BftProtocolSchedule(final DefaultProtocolSchedule protocolSchedule) {
    super(protocolSchedule);
  }

  /**
   * Look up ProtocolSpec by block number
   *
   * @param number block number
   * @return the protocol spec for that block number
   */
  public ProtocolSpec getByBlockNumber(final long number) {
    checkArgument(number >= 0, "number must be non-negative");
    checkArgument(
        !protocolSpecs.isEmpty(), "At least 1 milestone must be provided to the protocol schedule");
    checkArgument(
        protocolSpecs.last().fork().milestone() == 0,
        "There must be a milestone starting from block 0");
    // protocolSpecs is sorted in descending block order, so the first one we find that's lower than
    // the requested level will be the most appropriate spec
    for (final ScheduledProtocolSpec s : protocolSpecs) {
      if (number >= s.fork().milestone()) {
        return s.spec();
      }
    }
    return null;
  }

  /**
   * return the ordered list of scheduled protocol specs
   *
   * @return the scheduled protocol specs
   */
  public List<ScheduledProtocolSpec> getScheduledProtocolSpecs() {
    return protocolSpecs.stream().toList();
  }
}
