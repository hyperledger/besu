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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.era1.Era1Type;
import org.hyperledger.besu.util.io.OutputStreamFactory;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.util.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.SnappyFramedOutputStream;

/** A class to export ERA1 files */
public class Era1BlockExporter {
  private static final Logger LOG = LoggerFactory.getLogger(Era1BlockExporter.class);
  private static final long ERA1_FILE_BLOCKS = 8192;
  private static final String MAINNET_GENESIS_HASH =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
  private static final String SEPOLIA_GENESIS_HASH =
      "0x25a5cc106eea7138acab33231d7160d69cb777ee0c2c553fcddf5138993e6dd9";

  private final Blockchain blockchain;
  private final OutputStreamFactory outputStreamFactory;
  private final SnappyFactory snappyFactory;

  /**
   * Instantiates a new ERA1 block exporter.
   *
   * @param blockchain the blockchain
   * @param outputStreamFactory an OutputStreamFactory
   * @param snappyFactory a SnappyFactory
   */
  public Era1BlockExporter(
      final Blockchain blockchain,
      final OutputStreamFactory outputStreamFactory,
      final SnappyFactory snappyFactory) {
    this.blockchain = blockchain;
    this.outputStreamFactory = outputStreamFactory;
    this.snappyFactory = snappyFactory;
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
                      .ifPresent((receipts) -> transactionReceiptsForFile.put(block, receipts));
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

      String filename =
          String.format(
              "%s-%05d-%s.era1",
              network,
              fileNumber,
              blocksForFile.getLast().getHeader().getStateRoot().toFastHex(false).substring(0, 8));
      try (FileOutputStream writer =
          outputStreamFactory.createFileOutputStream(
              outputDirectory.toPath().resolve(filename).toFile())) {
        long position = 0;
        position += writeSection(writer, Era1Type.VERSION, new byte[] {});

        Map<Block, Long> blockPositions = new HashMap<>();
        for (Block block : blocksForFile) {
          blockPositions.put(block, position);
          position +=
              writeCompressedSection(
                  writer,
                  Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER,
                  RLP.encode(block.getHeader()::writeTo).toArray());
          position +=
              writeCompressedSection(
                  writer,
                  Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY,
                  RLP.encode(block.getBody()::writeTo).toArray());
          position +=
              writeCompressedSection(
                  writer,
                  Era1Type.COMPRESSED_EXECUTION_BLOCK_RECEIPTS,
                  RLP.encode(
                          (rlpOutput) ->
                              transactionReceiptsForFile
                                  .get(block)
                                  .forEach(
                                      (tr) ->
                                          TransactionReceiptEncoder.writeTo(
                                              tr,
                                              rlpOutput,
                                              TransactionReceiptEncodingConfiguration.DEFAULT)))
                      .toArray());
          position +=
              writeSection(
                  writer, Era1Type.TOTAL_DIFFICULTY, difficultysForFile.get(block).toArray());
        }

        Hash accumulatorHash =
            Util.getRootFromListOfBytes(
                accumulatorHeaderRecords.stream()
                    .map(
                        (ahr) -> {
                          ByteBuffer bytes =
                              ByteBuffer.allocate(
                                  ahr.blockHash.size() + ahr.totalDifficulty.size());
                          ahr.blockHash.appendTo(bytes);
                          ahr.totalDifficulty.appendTo(bytes);
                          return Bytes.wrapByteBuffer(bytes);
                        })
                    .toList());
        position += writeSection(writer, Era1Type.ACCUMULATOR, accumulatorHash.toArray());

        ByteBuffer blockIndex = ByteBuffer.allocate(16 + blockPositions.size() * 8);
        blockIndex.put(Pack.longToLittleEndian(startBlock));
        for (Block block : blocksForFile) {
          long relativePosition = blockPositions.get(block) - position;
          blockIndex.put(Pack.longToLittleEndian(relativePosition));
        }
        blockIndex.put(Pack.longToLittleEndian(blocksForFile.size()));
        position += writeSection(writer, Era1Type.BLOCK_INDEX, blockIndex.array());
        LOG.info("Wrote {} bytes to {}", position, filename);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private long writeSection(
      final FileOutputStream writer, final Era1Type era1Type, final byte[] content)
      throws IOException {
    byte[] typeCode = era1Type.getTypeCode();
    writer.write(typeCode);
    byte[] length = convertLengthToLittleEndianByteArray(content.length);
    writer.write(length);
    writer.write(content);

    return (long) (typeCode.length + length.length + content.length);
  }

  private long writeCompressedSection(
      final FileOutputStream writer, final Era1Type era1Type, final byte[] content)
      throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (SnappyFramedOutputStream snappyOutputStream =
        snappyFactory.createFramedOutputStream(byteArrayOutputStream)) {
      snappyOutputStream.write(content);
    }

    return writeSection(writer, era1Type, byteArrayOutputStream.toByteArray());
  }

  private byte[] convertLengthToLittleEndianByteArray(final int length) {
    byte[] lengthBytes = Pack.intToLittleEndian(length);
    return new byte[] {lengthBytes[0], lengthBytes[1], lengthBytes[2], lengthBytes[3], 0, 0};
  }

  private record AccumulatorHeaderRecord(Bytes32 blockHash, UInt256 totalDifficulty) {}
  ;
}
