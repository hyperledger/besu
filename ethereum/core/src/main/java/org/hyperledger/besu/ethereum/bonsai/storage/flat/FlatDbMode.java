package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public enum FlatDbMode {
  PARTIAL(Bytes.of(0x00)),
  FULL(Bytes.of(0x01));

  final Bytes version;

  FlatDbMode(final Bytes version) {
    this.version = version;
  }

  public Bytes getVersion() {
    return version;
  }

  public static FlatDbMode fromVersion(final Bytes version) {
    return Stream.of(FlatDbMode.values())
        .filter(mode -> mode.getVersion().equals(version))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown flat DB mode version: " + version));
  }
}
