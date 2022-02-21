/*
 * Copyright Hyperledger Besu contributors
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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

@JsonPropertyOrder({
  "number",
  "blockHash",
  "mixHash",
  "parentHash",
  "nonce",
  "sha3Uncles",
  "logsBloom",
  "transactionsRoot",
  "stateRoot",
  "receiptsRoot",
  "miner",
  "difficulty",
  "totalDifficulty",
  "extraData",
  "baseFee",
  "size",
  "gasLimit",
  "gasUsed",
  "timestamp",
  "uncles",
  "transactions"
})
public class ConsensusBlockResult extends BlockResult {

  private final List<String> opaqueTransactions;

  public ConsensusBlockResult(
      final BlockHeader header,
      final List<String> transactions,
      final List<JsonNode> ommers,
      final Difficulty totalDifficulty,
      final int size,
      final boolean includeCoinbase) {
    super(header, null, ommers, totalDifficulty, size, includeCoinbase);
    this.opaqueTransactions = transactions;
  }

  @Override
  @JsonGetter(value = "blockHash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "transactions")
  public List<String> getOpaqueTransactions() {
    return opaqueTransactions;
  }
}
