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
package org.hyperledger.besu.consensus.qbt.support.blockheader;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BftBlockHeaderHashTestCaseSpec {
  final BlockHeader blockHeader;
  final Hash onChainHash;
  final Hash committedSealHash;

  @JsonCreator
  public BftBlockHeaderHashTestCaseSpec(
      @JsonProperty("qbft_block_header") final QbftBlockHeader blockHeaderBuilder,
      @JsonProperty("on_chain_hash") final Hash onChainHash,
      @JsonProperty("committed_seal_hash") final Hash committedSealHash) {
    this.blockHeader = QbftBlockHeader.convertToCoreBlockHeader(blockHeaderBuilder);
    this.onChainHash = onChainHash;
    this.committedSealHash = committedSealHash;
  }

  public BlockHeader getBlockHeader() {
    return blockHeader;
  }

  public Hash getOnChainHash() {
    return onChainHash;
  }

  public Hash getCommittedSealHash() {
    return committedSealHash;
  }
}
