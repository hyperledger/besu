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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.era1.Era1Type;
import org.hyperledger.besu.util.ssz.Merkleizer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.util.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A class to export ERA1 files */
public class Era1BlockExporter {
  private static final Logger LOG = LoggerFactory.getLogger(Era1BlockExporter.class);
  private static final int ERA1_FILE_BLOCKS = 8192;
  private static final String MAINNET_GENESIS_HASH =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
  private static final String SEPOLIA_GENESIS_HASH =
      "0x25a5cc106eea7138acab33231d7160d69cb777ee0c2c553fcddf5138993e6dd9";

  private final Blockchain blockchain;
  private final Era1FileWriterFactory era1FileWriterFactory;
  private final Merkleizer merkleizer;

  /**
   * Instantiates a new ERA1 block exporter.
   *
   * @param blockchain the blockchain
   * @param era1FileWriterFactory a Era1FileWriterFactory
   */
  public Era1BlockExporter(
      final Blockchain blockchain, final Era1FileWriterFactory era1FileWriterFactory) {
    this.blockchain = blockchain;
    this.era1FileWriterFactory = era1FileWriterFactory;
    this.merkleizer = new Merkleizer();
  }

  /**
   * Exports ERA1 files starting from file startFile and ending at EndFile
   *
   * @param startFile The first ERA1 file to be exported
   * @param endFile The last ERA1 file to be exported
   * @param outputDirectory The directory in which to put the exported files
   */
  public void export(final long startFile, final long endFile, final File outputDirectory) {
    if (endFile < startFile) {
      throw new IllegalArgumentException("End of export range must be after start of export range");
    }
    LOG.info("Exporting ERA1 files {} to {} inclusive", startFile, endFile);
    for (long fileNumber = startFile; fileNumber <= endFile; fileNumber++) {
      long startBlock = fileNumber * ERA1_FILE_BLOCKS;
      long endBlock = startBlock + ERA1_FILE_BLOCKS - 1;

      List<Block> blocksForFile = new ArrayList<>();
      Map<Block, List<TransactionReceipt>> transactionReceiptsForFile = new HashMap<>();
      Map<Block, Difficulty> difficultysForFile = new HashMap<>();
      List<AccumulatorHeaderRecord> accumulatorHeaderRecords = new ArrayList<>();
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
                            difficultysForFile.put(block, difficulty);
                            accumulatorHeaderRecords.add(
                                new AccumulatorHeaderRecord(
                                    block.getHash(), difficulty.toUInt256()));
                          });
                });
      }
      String network =
          switch (blockchain.getGenesisBlockHeader().getHash().toString()) {
            case MAINNET_GENESIS_HASH -> "mainnet";
            case SEPOLIA_GENESIS_HASH -> "sepolia";
            default -> throw new RuntimeException("Unable to export ERA1 files for this network");
          };
      List<Bytes32> accumulatorBytesList =
          accumulatorHeaderRecords.stream()
              .map(
                  (ahr) ->
                      merkleizer.merkleizeChunks(
                          List.of(
                              ahr.blockHash,
                              Bytes32.wrap(ahr.totalDifficulty.toArray(ByteOrder.LITTLE_ENDIAN)))))
              .toList();
      Bytes32 accumulatorHash = merkleizer.merkleizeChunks(accumulatorBytesList, ERA1_FILE_BLOCKS);
      accumulatorHash =
          merkleizer.mixinLength(accumulatorHash, UInt256.valueOf(accumulatorHeaderRecords.size()));

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
              RLP.encode(block.getHeader()::writeTo).toArray());
          writer.writeSection(
              Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY,
              RLP.encode(block.getBody()::writeWrappedBodyTo).toArray());
          writer.writeSection(
              Era1Type.COMPRESSED_EXECUTION_BLOCK_RECEIPTS,
              RLP.encode(
                      (rlpOutput) -> {
                        List<TransactionReceipt> receipts = transactionReceiptsForFile.get(block);
                        if (receipts.isEmpty()) {
                          rlpOutput.writeEmptyList();
                        } else {
                          receipts.forEach(
                              (tr) ->
                                  TransactionReceiptEncoder.writeTo(
                                      tr,
                                      rlpOutput,
                                      TransactionReceiptEncodingConfiguration.DEFAULT));
                        }
                      })
                  .toArray());
          writer.writeSection(
              Era1Type.TOTAL_DIFFICULTY,
              difficultysForFile.get(block).toArray(ByteOrder.LITTLE_ENDIAN));
        }

        writer.writeSection(Era1Type.ACCUMULATOR, accumulatorHash.toArray());

        ByteBuffer blockIndex = ByteBuffer.allocate(16 + blockPositions.size() * 8);
        blockIndex.put(Pack.longToLittleEndian(startBlock));
        for (Block block : blocksForFile) {
          long relativePosition = blockPositions.get(block) - writer.getPosition();
          blockIndex.put(Pack.longToLittleEndian(relativePosition));
        }
        blockIndex.put(Pack.longToLittleEndian(blocksForFile.size()));
        writer.writeSection(Era1Type.BLOCK_INDEX, blockIndex.array());
        LOG.info("Wrote {} bytes to {}", writer.getPosition(), filename);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private record AccumulatorHeaderRecord(Bytes32 blockHash, UInt256 totalDifficulty) {}
  ;
}
