/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ZkTrieLogParameter {

  private final Long blockNumber;

  private final Hash blockHash;

  private final Boolean isSyncing;

  private final String trieLogRlpBytes;

  public Long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public boolean isSyncing() {
    return isSyncing;
  }

  public String gettrieLogRlpBytes() {
    return trieLogRlpBytes;
  }

  @JsonCreator
  public ZkTrieLogParameter(
      @JsonProperty("blockNumber") final Long blockNumber,
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("isSyncing") final boolean isSyncing,
      @JsonProperty("trieLogRlpBytes") final String trieLogRlpBytes) {
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.isSyncing = isSyncing;
    this.trieLogRlpBytes = trieLogRlpBytes;
  }

  @Override
  public String toString() {
    return "blockNumber="
        + blockNumber
        + ", blockHash="
        + blockHash
        + ", isSyncing="
        + isSyncing
        + ", trieLogRlpBytes="
        + trieLogRlpBytes;
  }
}
