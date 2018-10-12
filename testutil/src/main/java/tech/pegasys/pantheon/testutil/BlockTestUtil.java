package tech.pegasys.pantheon.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.io.Resources;

public final class BlockTestUtil {

  private BlockTestUtil() {
    // Utility Class
  }

  /**
   * Writes the first 1000 blocks of the public chain to the given file.
   *
   * @param target FIle to write blocks to
   */
  public static void write1000Blocks(final Path target) {
    try {
      Files.write(
          target,
          Resources.toByteArray(Resources.getResource("1000.blocks")),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
