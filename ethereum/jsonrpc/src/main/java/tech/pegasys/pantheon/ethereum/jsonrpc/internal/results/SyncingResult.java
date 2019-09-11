/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.plugin.data.SyncStatus;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"startingBlock", "currentBlock", "highestBlock"})
public class SyncingResult implements JsonRpcResult {

  private final String startingBlock;
  private final String currentBlock;
  private final String highestBlock;

  public SyncingResult(final SyncStatus syncStatus) {

    this.startingBlock = Quantity.create(syncStatus.getStartingBlock());
    this.currentBlock = Quantity.create(syncStatus.getCurrentBlock());
    this.highestBlock = Quantity.create(syncStatus.getHighestBlock());
  }

  @JsonGetter(value = "startingBlock")
  public String getStartingBlock() {
    return startingBlock;
  }

  @JsonGetter(value = "currentBlock")
  public String getCurrentBlock() {
    return currentBlock;
  }

  @JsonGetter(value = "highestBlock")
  public String getHighestBlock() {
    return highestBlock;
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof SyncingResult)) {
      return false;
    }
    final SyncingResult that = (SyncingResult) other;
    return this.startingBlock.equals(that.startingBlock)
        && this.currentBlock.equals(that.currentBlock)
        && this.highestBlock.equals(that.highestBlock);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startingBlock, currentBlock, highestBlock);
  }
}
