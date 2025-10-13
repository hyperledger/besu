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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

/** The Clique difficulty calculator. */
public class CliqueDifficultyCalculator implements DifficultyCalculator {

  private final Address localAddress;

  private final BigInteger IN_TURN_DIFFICULTY = BigInteger.valueOf(2);
  private final BigInteger OUT_OF_TURN_DIFFICULTY = BigInteger.ONE;

  /**
   * Instantiates a new Clique difficulty calculator.
   *
   * @param localAddress the local address
   */
  public CliqueDifficultyCalculator(final Address localAddress) {
    this.localAddress = localAddress;
  }

  @Override
  public BigInteger nextDifficulty(final long time, final BlockHeader parent) {

    final Address nextProposer = CliqueHelpers.getProposerForBlockAfter(parent);
    return nextProposer.equals(localAddress) ? IN_TURN_DIFFICULTY : OUT_OF_TURN_DIFFICULTY;
  }
}
