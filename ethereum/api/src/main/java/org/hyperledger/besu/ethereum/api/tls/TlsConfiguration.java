/*
 * Copyright (C) 2019 ConsenSys AG.
 *
 * The source code is provided to licensees of PegaSys Plus for their convenience and internal
 * business use. These files may not be copied, translated, and/or distributed without the express
 * written permission of an authorized signatory for ConsenSys AG.
 */
package org.hyperledger.besu.ethereum.api.tls;

import java.util.Optional;

public class TlsConfiguration {
  private final TlsStoreConfiguration keyStore;

  // trust store to verify identify of clients
  private final Optional<TlsStoreConfiguration> trustStore;

  public TlsConfiguration(
      final TlsStoreConfiguration keyStore, final Optional<TlsStoreConfiguration> trustStore) {
    this.keyStore = keyStore;
    this.trustStore = trustStore;
  }

  public TlsStoreConfiguration getKeyStore() {
    return keyStore;
  }

  public Optional<TlsStoreConfiguration> getTrustStore() {
    return trustStore;
  }
}
