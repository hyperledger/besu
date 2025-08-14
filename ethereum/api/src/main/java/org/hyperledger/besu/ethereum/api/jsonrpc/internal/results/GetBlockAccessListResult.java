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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Result object for block access list queries. */
@JsonPropertyOrder({"balHash", "blockAccessList"})
public class GetBlockAccessListResult {

  private final String balHash;
  private final BlockAccessListResult blockAccessList;

  public GetBlockAccessListResult(
      final String balHash, final BlockAccessListResult blockAccessList) {
    this.balHash = balHash;
    this.blockAccessList = blockAccessList;
  }

  @JsonGetter(value = "balHash")
  public String getBalHash() {
    return balHash;
  }

  @JsonGetter(value = "blockAccessList")
  public BlockAccessListResult getBlockAccessList() {
    return blockAccessList;
  }
}
