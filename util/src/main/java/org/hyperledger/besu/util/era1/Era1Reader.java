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
package org.hyperledger.besu.util.era1;

import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.SnappyFramedInputStream;

/** Reads era1 files */
public class Era1Reader {
  private static final Logger LOG = LoggerFactory.getLogger(Era1Reader.class);
  private static final int TYPE_LENGTH = 2;
  private static final int LENGTH_LENGTH = 6;
  private static final int STARTING_BLOCK_INDEX_LENGTH = 8;
  private static final int BLOCK_INDEX_LENGTH = 8;
  private static final int BLOCK_INDEX_COUNT_LENGTH = 8;

  private final SnappyFactory snappyFactory;

  /**
   * Creates a new Era1Reader with the supplied SnappyFactory
   *
   * @param snappyFactory A factory to provide objects for snappy decompression
   */
  public Era1Reader(final SnappyFactory snappyFactory) {
    this.snappyFactory = snappyFactory;
  }

  /**
   * Reads the entire supplied InputStream, calling appropriate methods on the supplied
   * Era1ReaderListener as different parts of the file are read
   *
   * @param inputStream The InputStream
   * @param listener the Era1ReaderListener
   * @throws IOException If there are any problems reading from the InputStream, or creating and
   *     using other streams, such as a SnappyFramedInputStream
   */
  public void read(final InputStream inputStream, final Era1ReaderListener listener)
      throws IOException {
    int blockIndex = 0;
    while (inputStream.available() > 0) {
      Era1Type type = Era1Type.getForTypeCode(inputStream.readNBytes(TYPE_LENGTH));
      int length = (int) convertLittleEndianBytesToLong(inputStream.readNBytes(LENGTH_LENGTH));
      switch (type) {
        case VERSION -> {
          // do nothing
        }
        case EMPTY, ACCUMULATOR, TOTAL_DIFFICULTY -> {
          // skip the bytes that were indicated to be empty
          // TODO read ACCUMULATOR and TOTAL_DIFFICULTY properly?
          inputStream.skipNBytes(length);
        }
        case COMPRESSED_EXECUTION_BLOCK_HEADER -> {
          byte[] compressedExecutionBlockHeader = inputStream.readNBytes(length);
          try (SnappyFramedInputStream decompressionStream =
              snappyFactory.createFramedInputStream(compressedExecutionBlockHeader)) {
            listener.handleExecutionBlockHeader(
                new Era1ExecutionBlockHeader(decompressionStream.readAllBytes(), blockIndex));
          }
        }
        case COMPRESSED_EXECUTION_BLOCK_BODY -> {
          byte[] compressedExecutionBlock = inputStream.readNBytes(length);
          try (SnappyFramedInputStream decompressionStream =
              snappyFactory.createFramedInputStream(compressedExecutionBlock)) {
            listener.handleExecutionBlockBody(
                new Era1ExecutionBlockBody(decompressionStream.readAllBytes(), blockIndex));
          }
        }
        case COMPRESSED_EXECUTION_BLOCK_RECEIPTS -> {
          byte[] compressedReceipts = inputStream.readNBytes(length);
          try (SnappyFramedInputStream decompressionStream =
              snappyFactory.createFramedInputStream(compressedReceipts)) {
            listener.handleExecutionBlockReceipts(
                new Era1ExecutionBlockReceipts(decompressionStream.readAllBytes(), blockIndex++));
          }
        }
        case BLOCK_INDEX -> {
          ByteArrayInputStream blockIndexInputStream =
              new ByteArrayInputStream(inputStream.readNBytes(length));
          long startingBlockIndex =
              convertLittleEndianBytesToLong(
                  blockIndexInputStream.readNBytes(STARTING_BLOCK_INDEX_LENGTH));
          List<Long> indexes = new ArrayList<>();
          while (blockIndexInputStream.available() > BLOCK_INDEX_COUNT_LENGTH) {
            indexes.add(
                convertLittleEndianBytesToLong(
                    blockIndexInputStream.readNBytes(BLOCK_INDEX_LENGTH)));
          }
          long indexCount =
              convertLittleEndianBytesToLong(
                  blockIndexInputStream.readNBytes(BLOCK_INDEX_COUNT_LENGTH));
          if (indexCount != indexes.size()) {
            LOG.warn(
                "index count does not match number of indexes present for InputStream: {}",
                inputStream);
          }
          listener.handleBlockIndex(new Era1BlockIndex(startingBlockIndex, indexes));
        }
      }
    }
  }

  private long convertLittleEndianBytesToLong(final byte[] bytes) {
    return Pack.littleEndianToLong(bytes, 0, bytes.length);
  }
}
