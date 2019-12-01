/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.util;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

public final class RawBlockIterator implements Iterator<Block>, Closeable {
  private static final int DEFAULT_INIT_BUFFER_CAPACITY = 1 << 16;

  private final FileChannel fileChannel;
  private final Function<RLPInput, BlockHeader> headerReader;
  private ByteBuffer readBuffer;

  private Block next;

  RawBlockIterator(
      final Path file,
      final Function<RLPInput, BlockHeader> headerReader,
      final int initialCapacity)
      throws IOException {
    fileChannel = FileChannel.open(file);
    this.headerReader = headerReader;
    readBuffer = ByteBuffer.allocate(initialCapacity);
    nextBlock();
  }

  public RawBlockIterator(final Path file, final Function<RLPInput, BlockHeader> headerReader)
      throws IOException {
    this(file, headerReader, DEFAULT_INIT_BUFFER_CAPACITY);
  }

  @Override
  public boolean hasNext() {
    return next != null;
  }

  @Override
  public Block next() {
    if (next == null) {
      throw new NoSuchElementException("No more blocks in found in the file.");
    }
    final Block result = next;
    try {
      nextBlock();
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
    return result;
  }

  @Override
  public void close() throws IOException {
    fileChannel.close();
  }

  private void nextBlock() throws IOException {
    fillReadBuffer();
    int initial = readBuffer.position();
    if (initial > 0) {
      final int length = RLP.calculateSize(Bytes.wrapByteBuffer(readBuffer));
      if (length > readBuffer.capacity()) {
        readBuffer.flip();
        final ByteBuffer newBuffer = ByteBuffer.allocate(2 * length);
        newBuffer.put(readBuffer);
        readBuffer = newBuffer;
        fillReadBuffer();
        initial = readBuffer.position();
      }

      final Bytes rlpBytes = Bytes.wrap(Bytes.wrapByteBuffer(readBuffer, 0, length).toArray());
      final RLPInput rlp = new BytesValueRLPInput(rlpBytes, false);
      rlp.enterList();
      final BlockHeader header = headerReader.apply(rlp);
      final BlockBody body =
          new BlockBody(rlp.readList(Transaction::readFrom), rlp.readList(headerReader));
      next = new Block(header, body);
      readBuffer.position(length);
      readBuffer.compact();
      readBuffer.position(initial - length);
    } else {
      next = null;
    }
  }

  private void fillReadBuffer() throws IOException {
    fileChannel.read(readBuffer);
  }
}
