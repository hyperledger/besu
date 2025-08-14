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
import org.hyperledger.besu.util.io.OutputStreamFactory;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xerial.snappy.SnappyFramedOutputStream;

public class Era1BlockExporter {
  private static final long ERA1_FILE_BLOCKS = 8192;
  public static final String MAINNET_GENESIS_HASH =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
  public static final String SEPOLIA_GENESIS_HASH =
      "0x25a5cc106eea7138acab33231d7160d69cb777ee0c2c553fcddf5138993e6dd9";

  private final Blockchain blockchain;
  private final OutputStreamFactory outputStreamFactory;
  private final SnappyFactory snappyFactory;

  /**
   * Instantiates a new ERA1 block exporter.
   *
   * @param blockchain the blockchain
   */
  public Era1BlockExporter(
      final Blockchain blockchain,
      final OutputStreamFactory outputStreamFactory,
      final SnappyFactory snappyFactory) {
    this.blockchain = blockchain;
    this.outputStreamFactory = outputStreamFactory;
    this.snappyFactory = snappyFactory;
  }

  public void export(final long startFile, final long endFile) {
    for (long fileNumber = startFile; fileNumber <= endFile; fileNumber++) {
      long startBlock = fileNumber * ERA1_FILE_BLOCKS;
      long endBlock = startBlock + ERA1_FILE_BLOCKS - 1;

      List<Block> blocksForFile = new ArrayList<>();
      Map<Block, List<TransactionReceipt>> transactionReceiptsForFile = new HashMap<>();
      Map<Block, Difficulty> difficultysForFile = new HashMap<>();
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
                      .ifPresent((difficulty) -> difficultysForFile.put(block, difficulty));
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
      try {
        FileOutputStream writer = outputStreamFactory.createFileOutputStream(filename);
        writer.write(Era1Type.VERSION.getTypeCode());

        for (Block block : blocksForFile) {
          writeCompressedSection(
              writer,
              Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER,
              RLP.encode(block.getHeader()::writeTo).toArray());
          writeCompressedSection(
              writer,
              Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY,
              RLP.encode(block.getBody()::writeTo).toArray());
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
          writeSection(writer, Era1Type.TOTAL_DIFFICULTY, difficultysForFile.get(block).toArray());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void writeSection(
      final FileOutputStream writer, final Era1Type era1Type, final byte[] content)
      throws IOException {
    writer.write(era1Type.getTypeCode());
    writer.write(convertLengthToByteArray(content.length));
    writer.write(content);
  }

  private void writeCompressedSection(
      final FileOutputStream writer, final Era1Type era1Type, final byte[] content)
      throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    SnappyFramedOutputStream snappyOutputStream =
        snappyFactory.createFramedOutputStream(byteArrayOutputStream);
    snappyOutputStream.write(content);

    writeSection(writer, era1Type, byteArrayOutputStream.toByteArray());
  }

  private byte[] convertLengthToByteArray(final int length) {
    return new byte[] {(byte) (length >> 16), (byte) (length >> 8), (byte) (length)};
  }
}
