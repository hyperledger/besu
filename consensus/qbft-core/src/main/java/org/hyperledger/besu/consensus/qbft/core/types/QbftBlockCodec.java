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

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/** Block encoding and decoding to and from RLP */
public interface QbftBlockCodec {
  /**
   * Read a block from RLP
   *
   * @param rlpInput RLP input
   * @param hashMode Hash mode to ensure the block hash is calculated correctly
   * @return The block
   */
  QbftBlock readFrom(RLPInput rlpInput, QbftHashMode hashMode);

  /**
   * Write a block to RLP
   *
   * @param block The block to write
   * @param rlpOutput RLP output to write the block to
   */
  void writeTo(QbftBlock block, RLPOutput rlpOutput);
}
