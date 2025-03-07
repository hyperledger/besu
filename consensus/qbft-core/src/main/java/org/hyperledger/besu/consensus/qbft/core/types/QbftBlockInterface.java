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
package org.hyperledger.besu.consensus.qbft.core.types;

/** Utility functions for QBFT blocks */
public interface QbftBlockInterface {

  /**
   * Create a new block using the supplied block with round number replaced. The hash must be for
   * the committed seal.
   *
   * @param proposalBlock the proposal block
   * @param roundNumber the round number
   * @return the new qbft block with updated round number
   */
  QbftBlock replaceRoundInBlock(QbftBlock proposalBlock, int roundNumber);
}
