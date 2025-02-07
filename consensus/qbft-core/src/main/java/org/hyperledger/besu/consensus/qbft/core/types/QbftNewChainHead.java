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
package org.hyperledger.besu.consensus.qbft.core.types;

import org.hyperledger.besu.consensus.common.bft.events.BftEvent;
import org.hyperledger.besu.consensus.common.bft.events.BftEvents;

/**
 * Event indicating that new chain head has been received
 *
 * @param newChainHeadHeader the new chain head header
 */
public record QbftNewChainHead(QbftBlockHeader newChainHeadHeader) implements BftEvent {

  @Override
  public BftEvents.Type getType() {
    return BftEvents.Type.NEW_CHAIN_HEAD;
  }

  /**
   * Gets new chain head header.
   *
   * @return the new chain head header
   */
  @Override
  public QbftBlockHeader newChainHeadHeader() {
    return newChainHeadHeader;
  }
}
