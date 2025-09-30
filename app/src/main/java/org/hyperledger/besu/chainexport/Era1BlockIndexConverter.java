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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.ethereum.core.Block;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.bouncycastle.util.Pack;

public class Era1BlockIndexConverter {
  public byte[] convert(
      final List<Block> blocks,
      final Map<Block, Long> blockPositionByBlock,
      final long positionInFile) {
    ByteBuffer blockIndex = ByteBuffer.allocate(16 + blockPositionByBlock.size() * 8);
    blockIndex.put(Pack.longToLittleEndian(blocks.getFirst().getHeader().getNumber()));
    for (Block block : blocks) {
      long relativePosition = blockPositionByBlock.get(block) - positionInFile;
      blockIndex.put(Pack.longToLittleEndian(relativePosition));
    }
    blockIndex.put(Pack.longToLittleEndian(blockPositionByBlock.size()));
    return blockIndex.array();
  }
}
