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

import org.hyperledger.besu.ethereum.core.Hash;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({"block", "hash", "rlp"})
public class BadBlockResult {

  private final BlockResult blockResult;
  private final String hash;
  private final String rlp;

  public BadBlockResult(final BlockResult blockResult, final Hash hash, final Bytes rlp) {
    this.blockResult = blockResult;
    this.hash = hash.toString();
    this.rlp = rlp.toHexString();
  }

  @JsonGetter(value = "block")
  public BlockResult getBlockResult() {
    return blockResult;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "rlp")
  public String getRlp() {
    return rlp;
  }
}
