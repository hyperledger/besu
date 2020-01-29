package org.hyperledger.besu.ethereum.api.tls;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class TlsClientAuthConfiguration {
  private final Optional<Path> knownClientsFile;
  private final boolean caClientsEnabled;

  private TlsClientAuthConfiguration(
      final Optional<Path> knownClientsFile, final boolean caClientsEnabled) {
    this.knownClientsFile = knownClientsFile;
    this.caClientsEnabled = caClientsEnabled;
  }

  public Optional<Path> getKnownClientsFile() {
    return knownClientsFile;
  }

  public boolean isCaClientsEnabled() {
    return caClientsEnabled;
  }

  public static final class Builder {
    private Path knownClientsFile;
    private boolean caClientsEnabled;

    private Builder() {}

    public static Builder aTlsClientAuthConfiguration() {
      return new Builder();
    }

    public Builder withKnownClientsFile(Path knownClientsFile) {
      this.knownClientsFile = knownClientsFile;
      return this;
    }

    public Builder withCaClientsEnabled(boolean caClientsEnabled) {
      this.caClientsEnabled = caClientsEnabled;
      return this;
    }

    public TlsClientAuthConfiguration build() {
      if (!caClientsEnabled) {
        Objects.requireNonNull(knownClientsFile, "Known Clients File is required");
      }
      return new TlsClientAuthConfiguration(
          Optional.ofNullable(knownClientsFile), caClientsEnabled);
    }
  }
}
