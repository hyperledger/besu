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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.consensus.common.bft.SynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer.FutureMessageHandler;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;

/**
 * A Future message handler that updates the peers estimated height to be that of the parent block
 * number of the received message.
 */
public class FutureMessageSynchronizerHandler implements FutureMessageHandler {
  private final SynchronizerUpdater synchronizerUpdater;

  /**
   * Instantiates a new Future message synchronizer handler.
   *
   * @param synchronizerUpdater the synchronizer updater
   */
  public FutureMessageSynchronizerHandler(final SynchronizerUpdater synchronizerUpdater) {
    this.synchronizerUpdater = synchronizerUpdater;
  }

  @Override
  public void handleFutureMessage(final long msgChainHeight, final Message message) {
    synchronizerUpdater.updatePeerChainState(msgChainHeight - 1, message.getConnection());
  }
}
