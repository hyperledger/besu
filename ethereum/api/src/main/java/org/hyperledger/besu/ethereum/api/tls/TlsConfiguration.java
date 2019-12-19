/*
 * Copyright (C) 2019 ConsenSys AG.
 *
 * The source code is provided to licensees of PegaSys Plus for their convenience and internal
 * business use. These files may not be copied, translated, and/or distributed without the express
 * written permission of an authorized signatory for ConsenSys AG.
 */
package org.hyperledger.besu.ethereum.api.tls;

import java.util.List;

public class TlsConfiguration {
  private Boolean tlsEnabled = false;

  // server certificate/key configuration
  private TlsStoreConfiguration serverKeyStore;

  // optional - trust store for server to verify identify of clients
  private TlsStoreConfiguration clientTrustKeyStore;

  // optional
  private List<String> cipherSuite;

  // optional - TLS protocols to remove from enabled list
  private List<String> removeEnabledTlsProtocol;

  // optional - default TLS versions already enabled in VertX - SSLv2Hello, TLSv1, TLSv1.1 and
  // TLSv1.2
  private List<String> addEnabledTlsProtocol;

  private boolean useOpenSSLEngine = false; // Use OpenSSL engine instead of JDK implementation
}
