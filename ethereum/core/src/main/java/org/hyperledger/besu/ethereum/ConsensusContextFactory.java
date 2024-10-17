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
package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

/** The ConsensusContextFactory interface defines a method for creating a consensus context. */
@FunctionalInterface
public interface ConsensusContextFactory {
  /** Helper for when you do not need a consensus context */
  ConsensusContextFactory NULL = (pc, ps) -> null;

  /**
   * Creates a consensus context with the given blockchain, world state archive, and protocol
   * schedule.
   *
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @return the created consensus context
   */
  ConsensusContext create(ProtocolContext protocolContext, ProtocolSchedule protocolSchedule);
}
