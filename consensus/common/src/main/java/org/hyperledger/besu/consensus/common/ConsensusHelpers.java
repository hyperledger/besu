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

import org.apache.tuweni.bytes.Bytes;

/** The Consensus helpers. */
public class ConsensusHelpers {
  /** Default constructor. */
  private ConsensusHelpers() {}

  /**
   * Zero left pad bytes.
   *
   * @param input the input
   * @param requiredLength the required length
   * @return the bytes
   */
  public static Bytes zeroLeftPad(final Bytes input, final int requiredLength) {
    final int paddingByteCount = Math.max(0, requiredLength - input.size());
    return Bytes.concatenate(Bytes.wrap(new byte[paddingByteCount]), input)
        .slice(0, requiredLength);
  }
}
