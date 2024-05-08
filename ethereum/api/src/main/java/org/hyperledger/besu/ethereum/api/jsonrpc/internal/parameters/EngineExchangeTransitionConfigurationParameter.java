/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The type Engine exchange transition configuration parameter. */
public class EngineExchangeTransitionConfigurationParameter {
  private final Difficulty terminalTotalDifficulty;
  private final Hash terminalBlockHash;
  private final long terminalBlockNumber;

  /**
   * Instantiates a new Engine exchange transition configuration parameter.
   *
   * @param terminalTotalDifficulty the terminal total difficulty
   * @param terminalBlockHash the terminal block hash
   * @param terminalBlockNumber the terminal block number
   */
  @JsonCreator
  public EngineExchangeTransitionConfigurationParameter(
      @JsonProperty("terminalTotalDifficulty") final String terminalTotalDifficulty,
      @JsonProperty("terminalBlockHash") final String terminalBlockHash,
      @JsonProperty("terminalBlockNumber") final UnsignedLongParameter terminalBlockNumber) {
    this.terminalTotalDifficulty = Difficulty.fromHexString(terminalTotalDifficulty);
    this.terminalBlockHash = Hash.fromHexString(terminalBlockHash);
    this.terminalBlockNumber = terminalBlockNumber.getValue();
  }

  /**
   * Gets terminal total difficulty.
   *
   * @return the terminal total difficulty
   */
  public Difficulty getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  /**
   * Gets terminal total difficulty as hex string.
   *
   * @return the terminal total difficulty as hex string
   */
  @JsonProperty("terminalTotalDifficulty")
  public String getTerminalTotalDifficultyAsHexString() {
    return terminalTotalDifficulty.toShortHexString();
  }

  /**
   * Gets terminal block hash.
   *
   * @return the terminal block hash
   */
  public Hash getTerminalBlockHash() {
    return terminalBlockHash;
  }

  /**
   * Gets terminal block hash as hex string.
   *
   * @return the terminal block hash as hex string
   */
  @JsonProperty("terminalBlockHash")
  public String getTerminalBlockHashAsHexString() {
    return terminalBlockHash.toShortHexString();
  }

  /**
   * Gets terminal block number.
   *
   * @return the terminal block number
   */
  public long getTerminalBlockNumber() {
    return terminalBlockNumber;
  }
}
