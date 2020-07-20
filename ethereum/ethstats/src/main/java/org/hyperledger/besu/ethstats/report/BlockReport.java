/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.hyperledger.besu.ethstats.report;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BlockReport {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("block")
  private final BlockResult block;

  @JsonCreator
  public BlockReport(final String id, final BlockResult block) {
    this.id = id;
    this.block = block;
  }

  public String getId() {
    return id;
  }

  public BlockResult getBlock() {
    return block;
  }
}
