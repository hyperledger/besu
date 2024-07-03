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
package org.hyperledger.besu.consensus.common.bft.events;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;

/** Static helper functions for producing and working with BftEvent objects */
public class BftEvents {
  /** Default constructor. */
  private BftEvents() {}

  /**
   * Instantiate BftEvent From message.
   *
   * @param message the message
   * @return the bft event
   */
  public static BftEvent fromMessage(final Message message) {
    return new BftReceivedMessageEvent(message);
  }

  /** The enum Type. */
  public enum Type {
    /** Round expiry type. */
    ROUND_EXPIRY,
    /** New chain head type. */
    NEW_CHAIN_HEAD,
    /** Block timer expiry type. */
    BLOCK_TIMER_EXPIRY,
    /** Message type. */
    MESSAGE
  }
}
