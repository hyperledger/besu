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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"terminalTotalDifficulty", "terminalBlockHash", "terminalBlockNumber"})
public class EngineExchangeTransitionConfigurationResult {
  private final Difficulty terminalTotalDifficulty;
  private final Hash terminalBlockHash;
  private final long terminalBlockNumber;

  public EngineExchangeTransitionConfigurationResult(
      final Difficulty terminalTotalDifficulty,
      final Hash terminalBlockHash,
      final long terminalBlockNumber) {
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    this.terminalBlockHash = terminalBlockHash;
    this.terminalBlockNumber = terminalBlockNumber;
  }

  @JsonGetter(value = "terminalTotalDifficulty")
  public String getTerminalTotalDifficultyAsString() {
    return Quantity.create(this.terminalTotalDifficulty.getAsBigInteger());
  }

  public Difficulty getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  @JsonGetter(value = "terminalBlockHash")
  public String getTerminalBlockHashAsString() {
    return terminalBlockHash.toHexString();
  }

  public Hash getTerminalBlockHash() {
    return terminalBlockHash;
  }

  @JsonGetter(value = "terminalBlockNumber")
  public String getTerminalBlockNumberAsString() {
    return Quantity.create(this.terminalBlockNumber);
  }

  public Long getTerminalBlockNumber() {
    return terminalBlockNumber;
  }
}
