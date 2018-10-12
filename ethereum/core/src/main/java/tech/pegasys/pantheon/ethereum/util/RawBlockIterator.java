package tech.pegasys.pantheon.ethereum.util;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RlpUtils;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

public final class RawBlockIterator implements Iterator<Block>, Closeable {

  private final FileChannel fileChannel;
  private final Function<RLPInput, BlockHeader> headerReader;

  private ByteBuffer readBuffer = ByteBuffer.allocate(2 << 15);

  private Block next;

  public RawBlockIterator(final Path file, final Function<RLPInput, BlockHeader> headerReader)
      throws IOException {
    fileChannel = FileChannel.open(file);
    this.headerReader = headerReader;
    nextBlock();
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
      final int length = RlpUtils.decodeLength(readBuffer, 0);
      if (length > readBuffer.capacity()) {
        readBuffer.flip();
        final ByteBuffer newBuffer = ByteBuffer.allocate(2 * length);
        newBuffer.put(readBuffer);
        readBuffer = newBuffer;
        fillReadBuffer();
        initial = readBuffer.position();
      }
      final RLPInput rlp =
          new BytesValueRLPInput(BytesValue.wrap(Arrays.copyOf(readBuffer.array(), length)), false);
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
