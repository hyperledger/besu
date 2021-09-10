package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.immutables.value.Value;

@Value.Immutable
public interface SnapSyncConfiguration {

  SnapSyncConfiguration DEFAULT_CONFIG =
      ImmutableSnapSyncConfiguration.builder().snapSyncEnabled(false).build();

  static SnapSyncConfiguration snapSyncDisabled() {
    return DEFAULT_CONFIG;
  }

  boolean snapSyncEnabled();
}
