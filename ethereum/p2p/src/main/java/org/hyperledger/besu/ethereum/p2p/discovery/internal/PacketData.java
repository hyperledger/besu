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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.time.Instant;

public interface PacketData {

  /**
   * Expiration is not standardised. We use Geth's expiration period (60 seconds); whereas Parity's
   * is 20 seconds.
   */
  long DEFAULT_EXPIRATION_PERIOD_SEC = 60;

  /**
   * Serializes the implementing packet data onto the provided RLP output buffer.
   *
   * @param out The RLP output buffer.
   */
  void writeTo(RLPOutput out);

  static long defaultExpiration() {
    return Instant.now().getEpochSecond() + DEFAULT_EXPIRATION_PERIOD_SEC;
  }
}
