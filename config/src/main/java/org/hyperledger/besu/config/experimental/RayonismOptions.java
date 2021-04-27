package org.hyperledger.besu.config.experimental;

import java.util.Optional;

/**
 * This is a "TODO" class. Eventually we will need to gate on total difficulty to determine when/if
 * to enable merge behavior. For now there is a static config that is driven by a command line
 * option.
 */
public class RayonismOptions {
  private static Optional<Boolean> mergeEnabled = Optional.empty();

  public static void setMergeEnabled(final boolean bool) {
    if (!mergeEnabled.isPresent()) {
      mergeEnabled = Optional.of(bool);
    } else if (mergeEnabled.get() != bool) {
      throw new RuntimeException(
          "Refusing to re-configure already configured rayonism merge feature");
    }
  }

  public static boolean isMergeEnabled() {
    return mergeEnabled.orElse(false);
  }
}
