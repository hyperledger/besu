/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.stream.Collectors.toList;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;

import java.util.List;
import java.util.function.UnaryOperator;

public class FastSyncCheckpointFilter implements UnaryOperator<List<BlockHeader>> {

  private final BlockHeader pivotBlockHeader;

  public FastSyncCheckpointFilter(final BlockHeader pivotBlockHeader) {
    this.pivotBlockHeader = pivotBlockHeader;
  }

  @Override
  public List<BlockHeader> apply(final List<BlockHeader> blockHeaders) {
    if (blockHeaders.isEmpty()) {
      return blockHeaders;
    }
    if (lastHeaderNumberIn(blockHeaders) > pivotBlockHeader.getNumber()) {
      return trimToPivotBlock(blockHeaders);
    }
    return blockHeaders;
  }

  private List<BlockHeader> trimToPivotBlock(final List<BlockHeader> blockHeaders) {
    final List<BlockHeader> filteredHeaders =
        blockHeaders.stream()
            .filter(header -> header.getNumber() <= pivotBlockHeader.getNumber())
            .collect(toList());
    if (filteredHeaders.isEmpty()
        || lastHeaderNumberIn(filteredHeaders) != pivotBlockHeader.getNumber()) {
      filteredHeaders.add(pivotBlockHeader);
    }
    return filteredHeaders;
  }

  private long lastHeaderNumberIn(final List<BlockHeader> filteredHeaders) {
    return filteredHeaders.get(filteredHeaders.size() - 1).getNumber();
  }
}
