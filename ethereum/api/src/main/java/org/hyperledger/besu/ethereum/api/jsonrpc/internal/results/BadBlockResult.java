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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.core.Block;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/** The interface Bad block result. */
@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableBadBlockResult.class)
@JsonDeserialize(as = ImmutableBadBlockResult.class)
@JsonPropertyOrder({"block", "hash", "rlp"})
public interface BadBlockResult {

  /**
   * Gets block result.
   *
   * @return the block result
   */
  @JsonProperty("block")
  BlockResult getBlockResult();

  /**
   * Gets hash.
   *
   * @return the hash
   */
  @JsonProperty("hash")
  String getHash();

  /**
   * Gets rlp.
   *
   * @return the rlp
   */
  @JsonProperty("rlp")
  String getRlp();

  /**
   * From bad block result.
   *
   * @param blockResult the block result
   * @param block the block
   * @return the bad block result
   */
  static BadBlockResult from(final BlockResult blockResult, final Block block) {
    return ImmutableBadBlockResult.of(
        blockResult, block.getHash().toHexString(), block.toRlp().toHexString());
  }
}
