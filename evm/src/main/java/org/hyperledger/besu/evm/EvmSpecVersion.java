package org.hyperledger.besu.evm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EvmSpecVersion {
  FRONTIER(0, true),
  HOMESTEAD(0, true),
  BYZANTIUM(0, true),
  CONSTANTINOPLE(0, true),
  ISTANBUL(0, true),
  LONDON(0, true),
  PARIS(0, true),
  SHANGHAI(1, false),

  /** Transient fork, will be removed */
  SHANDONG(1, false);

  private static final Logger LOGGER = LoggerFactory.getLogger(EvmSpecVersion.class);

  final boolean specFinalized;
  final int maxEofVersion;

  boolean versionWarned = false;

  EvmSpecVersion(final int maxEofVersion, final boolean specFinalized) {
    this.maxEofVersion = maxEofVersion;
    this.specFinalized = specFinalized;
  }

  public int getMaxEofVersion() {
    return maxEofVersion;
  }

  @SuppressWarnings("AlreadyChecked") // false positive
  public void maybeWarnVersion() {
    if (versionWarned) {
      return;
    }

    LOGGER.error(
        "****** Not for Production Network Use ******\nExecuting code from EVM Spec Version {}, which has not been finalized.\n****** Not for Production Network Use ******",
        this.name());
    versionWarned = true;
  }
}
