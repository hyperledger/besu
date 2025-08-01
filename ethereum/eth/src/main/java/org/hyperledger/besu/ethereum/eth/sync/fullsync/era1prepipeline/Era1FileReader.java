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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.util.era1.Era1BlockIndex;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockBody;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockHeader;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockReceipts;
import org.hyperledger.besu.util.era1.Era1Reader;
import org.hyperledger.besu.util.era1.Era1ReaderListener;
import org.hyperledger.besu.util.io.InputStreamFactory;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Era1FileReader implements Function<URI, CompletableFuture<List<Block>>> {
  private static final Logger LOG = LoggerFactory.getLogger(Era1FileReader.class);
  private static final int ERA1_BLOCK_COUNT_MAX = 8192;

  private final BlockHeaderFunctions blockHeaderFunctions;
  private final EthScheduler ethScheduler;

  public Era1FileReader(
      final BlockHeaderFunctions blockHeaderFunctions, final EthScheduler ethScheduler) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    this.ethScheduler = ethScheduler;
  }

  @Override
  public CompletableFuture<List<Block>> apply(final URI pathUri) {
    return ethScheduler.scheduleServiceTask(
        () -> {
          LOG.info("Reading {} and producing blocks for import", pathUri.toString());
          Era1Reader reader = new Era1Reader(new SnappyFactory(), new InputStreamFactory());

          final List<BlockHeader> headersFutures = new ArrayList<>(ERA1_BLOCK_COUNT_MAX);
          final List<BlockBody> bodiesFutures = new ArrayList<>(ERA1_BLOCK_COUNT_MAX);
          try {
            reader.read(
                pathUri.toURL().openStream(),
                new Era1ReaderListener() {

                  @Override
                  public void handleExecutionBlockHeader(
                      final Era1ExecutionBlockHeader executionBlockHeader) {
                    headersFutures.add(
                        BlockHeader.readFrom(
                            new BytesValueRLPInput(
                                Bytes.wrap(executionBlockHeader.header()), false),
                            blockHeaderFunctions));
                  }

                  @Override
                  public void handleExecutionBlockBody(
                      final Era1ExecutionBlockBody executionBlockBody) {
                    bodiesFutures.add(
                        BlockBody.readWrappedBodyFrom(
                            new BytesValueRLPInput(Bytes.wrap(executionBlockBody.block()), false),
                            blockHeaderFunctions,
                            true));
                  }

                  @Override
                  public void handleExecutionBlockReceipts(
                      final Era1ExecutionBlockReceipts executionBlockReceipts) {
                    // Not needed for FULL sync
                  }

                  @Override
                  public void handleBlockIndex(final Era1BlockIndex blockIndex) {
                    // not necessary, do nothing
                  }
                });
          } catch (IOException e) {
            LOG.error("Failed reading {} and creating blocks", pathUri, e);
            throw new RuntimeException(e);
          }

          List<Block> blocks = new ArrayList<>(ERA1_BLOCK_COUNT_MAX);
          for (int i = 0; i < headersFutures.size(); i++) {
            blocks.add(new Block(headersFutures.get(i), bodiesFutures.get(i)));
          }

          return CompletableFuture.completedFuture(blocks);
        });
  }
}
