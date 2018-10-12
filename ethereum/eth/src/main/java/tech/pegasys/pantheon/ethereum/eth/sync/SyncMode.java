package tech.pegasys.pantheon.ethereum.eth.sync;

public enum SyncMode {
  // Fully validate all blocks as they sync
  FULL,
  // Perform light validation on older blocks, and switch to full validation for more recent blocks
  FAST;

  public static SyncMode fromString(final String str) {
    for (final SyncMode mode : SyncMode.values()) {
      if (mode.name().equalsIgnoreCase(str)) {
        return mode;
      }
    }
    return null;
  }
}
