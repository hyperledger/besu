package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.util.RawBlockIterator;
import net.consensys.pantheon.testutil.BlockTestUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.rules.TemporaryFolder;

public final class BlockSyncTestUtils {

  private BlockSyncTestUtils() {
    // Utility Class
  }

  public static List<Block> firstBlocks(final int count) {
    final List<Block> result = new ArrayList<>(count);
    final TemporaryFolder temp = new TemporaryFolder();
    try {
      temp.create();
      final Path blocks = temp.newFile().toPath();
      BlockTestUtil.write1000Blocks(blocks);
      try (final RawBlockIterator iterator =
          new RawBlockIterator(
              blocks, rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash))) {
        for (int i = 0; i < count; ++i) {
          result.add(iterator.next());
        }
      }
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    } finally {
      temp.delete();
    }
    return result;
  }
}
