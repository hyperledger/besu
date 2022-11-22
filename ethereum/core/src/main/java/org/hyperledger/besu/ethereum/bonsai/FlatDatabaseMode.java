package org.hyperledger.besu.ethereum.bonsai;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;

public enum FlatDatabaseMode {
  FULL(Bytes.of(0x00)),
  PARTIAL(Bytes.of(0x01));

  Bytes version;

  FlatDatabaseMode(final Bytes version) {
    this.version = version;
  }

  public static FlatDatabaseMode fromBytes(final Bytes version) {
    return Arrays.stream(values())
        .filter(v -> v.version.equals(version))
        .findFirst()
        .orElse(PARTIAL);
  }
}
