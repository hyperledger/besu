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

import org.hyperledger.besu.crypto.SECPSignature;

import java.util.Collection;

/** Responsible for creating a block. */
public interface QbftBlockCreator {

  /**
   * Create a block.
   *
   * @param headerTimeStampSeconds the header timestamp
   * @param parentHeader the parent header
   * @return the block
   */
  QbftBlock createBlock(long headerTimeStampSeconds, QbftBlockHeader parentHeader);

  /**
   * Create sealed block.
   *
   * @param qbftExtraDataProvider the extra data provider
   * @param block the block
   * @param roundNumber the round number
   * @param commitSeals the commit seals
   * @return the block
   */
  QbftBlock createSealedBlock(
      final QbftExtraDataProvider qbftExtraDataProvider,
      final QbftBlock block,
      final int roundNumber,
      final Collection<SECPSignature> commitSeals);
}
