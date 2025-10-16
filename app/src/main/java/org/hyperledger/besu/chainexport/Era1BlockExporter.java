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

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.BlockBodyEncoder;
import org.hyperledger.besu.ethereum.core.encoding.BlockHeaderEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.util.era1.Era1Type;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A class to export ERA1 files */
public class Era1BlockExporter {
  private static final Logger LOG = LoggerFactory.getLogger(Era1BlockExporter.class);
  private static final int ERA1_FILE_BLOCKS = 8192;

  private final Blockchain blockchain;
  private final String network;
  private final Era1FileWriterFactory era1FileWriterFactory;
  private final Era1AccumulatorFactory era1AccumulatorFactory;
  private final Era1BlockIndexConverter era1BlockIndexConverter;
  private final BlockHeaderEncoder blockHeaderEncoder;
  private final BlockBodyEncoder blockBodyEncoder;
  private final TransactionReceiptEncoder transactionReceiptEncoder;

  /**
   * Instantiates a new ERA1 block exporter.
   *
   * @param blockchain the blockchain
   * @param networkName the network
   * @param era1FileWriterFactory an Era1FileWriterFactory
   * @param era1AccumulatorFactory an Era1AccumulatorFactory
   * @param era1BlockIndexConverter an Era1BlockIndexConverter
   * @param blockHeaderEncoder a BlockHeaderEncoder
   * @param blockBodyEncoder a BlockBodyEncoder
   * @param transactionReceiptEncoder a TransactionReceiptEncoder
   */
  public Era1BlockExporter(
      final Blockchain blockchain,
      final NetworkName networkName,
      final Era1FileWriterFactory era1FileWriterFactory,
      final Era1AccumulatorFactory era1AccumulatorFactory,
      final Era1BlockIndexConverter era1BlockIndexConverter,
      final BlockHeaderEncoder blockHeaderEncoder,
      final BlockBodyEncoder blockBodyEncoder,
      final TransactionReceiptEncoder transactionReceiptEncoder) {
    this.blockchain = blockchain;
    this.network =
        switch (networkName) {
          case MAINNET, SEPOLIA -> networkName.name().toLowerCase(Locale.getDefault());
          default ->
              throw new RuntimeException(
                  "Unable to export ERA1 files for " + networkName + " network");
        };
    this.era1FileWriterFactory = era1FileWriterFactory;
    this.era1AccumulatorFactory = era1AccumulatorFactory;
    this.era1BlockIndexConverter = era1BlockIndexConverter;
    this.blockHeaderEncoder = blockHeaderEncoder;
    this.blockBodyEncoder = blockBodyEncoder;
    this.transactionReceiptEncoder = transactionReceiptEncoder;
  }

  /**
   * Exports ERA1 files starting from the file containing requestedStartBlock and ending at the file
   * containing requestedEndBlock
   *
   * @param requestedStartBlock The requested start block
   * @param requestedEndBlock The requested end block
   * @param outputDirectory The directory in which to put the exported files
   */
  public void export(
      final long requestedStartBlock, final long requestedEndBlock, final File outputDirectory) {
    long startFile = convertBlockNumberToFileNumber(requestedStartBlock);
    long endFile = convertBlockNumberToFileNumber(requestedEndBlock);
    if (endFile < startFile) {
      throw new IllegalArgumentException("End of export range must be after start of export range");
    }
    LOG.info(
        "Exporting ERA1 files {} to {} inclusive for network: {}", startFile, endFile, network);
    for (long fileNumber = startFile; fileNumber <= endFile; fileNumber++) {
      long startBlock = fileNumber * ERA1_FILE_BLOCKS;
      long endBlock = startBlock + ERA1_FILE_BLOCKS - 1;

      List<Block> blocksForFile = new ArrayList<>();
      Map<Block, List<TransactionReceipt>> transactionReceiptsForFile = new HashMap<>();
      Map<Block, Difficulty> difficultiesForFile = new HashMap<>();
      Era1Accumulator accumulator = era1AccumulatorFactory.getEra1Accumulator();
      for (long blockNumber = startBlock; blockNumber <= endBlock; blockNumber++) {
        blockchain
            .getBlockByNumber(blockNumber)
            .ifPresent(
                (block) -> {
                  blocksForFile.add(block);
                  blockchain
                      .getTxReceipts(block.getHash())
                      .ifPresentOrElse(
                          (receipts) -> transactionReceiptsForFile.put(block, receipts),
                          () -> transactionReceiptsForFile.put(block, new ArrayList<>()));
                  blockchain
                      .getTotalDifficultyByHash(block.getHash())
                      .ifPresent(
                          (difficulty) -> {
                            difficultiesForFile.put(block, difficulty);
                            accumulator.addBlock(block.getHash(), difficulty.toUInt256());
                          });
                });
      }
      Bytes32 accumulatorHash = accumulator.accumulate();

      String filename =
          String.format(
              "%s-%05d-%s.era1",
              network, fileNumber, accumulatorHash.toFastHex(false).substring(0, 8));
      try (Era1FileWriter writer =
          era1FileWriterFactory.era1FileWriter(
              outputDirectory.toPath().resolve(filename).toFile())) {
        writer.writeSection(Era1Type.VERSION, new byte[] {});

        Map<Block, Long> blockPositions = new HashMap<>();
        for (Block block : blocksForFile) {
          blockPositions.put(block, writer.getPosition());
          writer.writeSection(
              Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER,
              blockHeaderEncoder.encode(block.getHeader()).toArray());
          writer.writeSection(
              Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY,
              blockBodyEncoder.encode(block.getBody()).toArray());
          writer.writeSection(
              Era1Type.COMPRESSED_EXECUTION_BLOCK_RECEIPTS,
              transactionReceiptEncoder
                  .encode(
                      transactionReceiptsForFile.get(block),
                      TransactionReceiptEncodingConfiguration.DEFAULT)
                  .toArray());
          writer.writeSection(
              Era1Type.TOTAL_DIFFICULTY,
              difficultiesForFile.get(block).toArray(ByteOrder.LITTLE_ENDIAN));
        }

        writer.writeSection(Era1Type.ACCUMULATOR, accumulatorHash.toArray());
        writer.writeSection(
            Era1Type.BLOCK_INDEX,
            era1BlockIndexConverter.convert(blocksForFile, blockPositions, writer.getPosition()));
        LOG.info("Wrote {} bytes to {}", writer.getPosition(), filename);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private long convertBlockNumberToFileNumber(final long blockNumber) {
    return blockNumber / ERA1_FILE_BLOCKS;
  }
}
