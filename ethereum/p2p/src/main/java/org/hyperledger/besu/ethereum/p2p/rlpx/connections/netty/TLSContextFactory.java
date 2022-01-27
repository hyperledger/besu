/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import org.hyperledger.besu.pki.keystore.HardwareKeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TLSContextFactory {

  private static final Logger LOG = LoggerFactory.getLogger(TLSContextFactory.class);

  private static final String[] ALLOWED_PROTOCOLS = {"TLSv1.3"};
  private static final String KEYMANAGER_FACTORY_ALGORITHM = "PKIX";
  private static final String TRUSTMANAGER_FACTORY_ALGORITHM = "PKIX";

  private List<String> enabledCipherSuites = null;
  private KeyManagerFactory kmf;
  private TrustManagerFactory tmf;
  private Collection<X509CRL> crls;
  private String[] allowedProtocols;

  protected TLSContextFactory() {}

  protected TLSContextFactory(
      final String keystorePass,
      final KeyStoreWrapper keystoreWrapper,
      final List<String> configuredCipherSuites,
      final String[] allowedProtocols)
      throws GeneralSecurityException, IOException {
    kmf = getKeyManagerFactory(keystoreWrapper.getKeyStore(), keystorePass);
    tmf = getTrustManagerFactory(keystoreWrapper.getTrustStore());
    crls = keystoreWrapper.getCRLs();
    if (configuredCipherSuites != null && !configuredCipherSuites.isEmpty()) {
      enabledCipherSuites = configuredCipherSuites;
    }
    this.allowedProtocols = null == allowedProtocols ? ALLOWED_PROTOCOLS : allowedProtocols;
  }

  public static TLSContextFactory getInstance(
      final String keyStorePassword, final KeyStoreWrapper wrapper, final String[] allowedProtocols)
      throws GeneralSecurityException, IOException {
    return new TLSContextFactory(keyStorePassword, wrapper, null, allowedProtocols);
  }

  public static TLSContextFactory getInstance(
      final String keyStorePassword, final KeyStoreWrapper wrapper)
      throws GeneralSecurityException, IOException {
    return new TLSContextFactory(keyStorePassword, wrapper, null, null);
  }

  public SslContext createNettyServerSslContext()
      throws NoSuchAlgorithmException, KeyManagementException {
    final List<String> enabledCipherSuites = getEnabledCipherSuites();

    return new JdkSslContext(
        createJavaSslContext(),
        false,
        enabledCipherSuites,
        IdentityCipherSuiteFilter.INSTANCE,
        null,
        ClientAuth.REQUIRE,
        null,
        false);
  }

  public SslContext createNettyClientSslContext()
      throws NoSuchAlgorithmException, KeyManagementException {
    final List<String> enabledCipherSuites = getEnabledCipherSuites();

    return new JdkSslContext(
        createJavaSslContext(),
        true,
        enabledCipherSuites,
        IdentityCipherSuiteFilter.INSTANCE,
        null,
        ClientAuth.NONE,
        null,
        false);
  }

  public SSLContext createJavaSslContext() throws NoSuchAlgorithmException, KeyManagementException {
    final SSLContext context = SSLContext.getInstance(getTlsProtocol());
    context.init(
        kmf.getKeyManagers(),
        null == crls || crls.isEmpty() ? tmf.getTrustManagers() : wrap(tmf.getTrustManagers()),
        null);
    return context;
  }

  protected TrustManager[] wrap(final TrustManager[] trustMgrs) {
    final TrustManager[] ret = trustMgrs.clone();
    for (int i = 0; i < ret.length; i++) {
      TrustManager trustMgr = ret[i];
      if (trustMgr instanceof X509TrustManager) {
        X509TrustManager x509TrustManager = (X509TrustManager) trustMgr;
        ret[i] =
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(
                  final X509Certificate[] x509Certificates, final String s)
                  throws CertificateException {
                checkRevoked(x509Certificates);
                x509TrustManager.checkClientTrusted(x509Certificates, s);
              }

              @Override
              public void checkServerTrusted(
                  final X509Certificate[] x509Certificates, final String s)
                  throws CertificateException {
                checkRevoked(x509Certificates);
                x509TrustManager.checkServerTrusted(x509Certificates, s);
              }

              private void checkRevoked(final X509Certificate[] x509Certificates)
                  throws CertificateException {
                for (X509CRL crl : crls) {
                  for (X509Certificate cert : x509Certificates) {
                    if (crl.isRevoked(cert)) {
                      throw new CertificateException("Certificate revoked");
                    }
                  }
                }
              }

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return x509TrustManager.getAcceptedIssuers();
              }
            };
      }
    }
    return ret;
  }

  protected TrustManagerFactory getTrustManagerFactory(final KeyStore truststore)
      throws GeneralSecurityException {
    final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TRUSTMANAGER_FACTORY_ALGORITHM);
    tmf.init(truststore);
    return tmf;
  }

  protected KeyManagerFactory getKeyManagerFactory(
      final KeyStore keystore, final String keystorePassword) throws GeneralSecurityException {
    final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KEYMANAGER_FACTORY_ALGORITHM);
    kmf.init(keystore, keystorePassword.toCharArray());
    return kmf;
  }

  private List<String> getEnabledCipherSuites() {
    final String protocol = getTlsProtocol();
    try {
      if (enabledCipherSuites == null || enabledCipherSuites.isEmpty()) {
        final SSLContext sslcontext = SSLContext.getInstance(protocol);
        sslcontext.init(null, null, null);
        enabledCipherSuites = Arrays.asList(sslcontext.createSSLEngine().getEnabledCipherSuites());
      }
    } catch (final NoSuchAlgorithmException | KeyManagementException e) {
      LOG.warn(
          "Could not get list of enabled (JDK) cipher suites for protocol:{}, reverting to Netty's default ones.",
          protocol);
    }
    return enabledCipherSuites;
  }

  private String getTlsProtocol() {
    return Arrays.stream(allowedProtocols).max(Comparator.naturalOrder()).orElse(null);
  }

  public static TLSContextFactory buildFrom(final TLSConfiguration config)
      throws GeneralSecurityException, IOException {
    TLSContextFactory ret = null;
    if (null != config) {
      LOG.info("Initializing SSL Context using {} keystore.", config.getKeyStoreType());
      KeyStoreWrapper wrapper =
          KeyStoreWrapper.KEYSTORE_TYPE_PKCS11.equalsIgnoreCase(config.getKeyStoreType())
              ? new HardwareKeyStoreWrapper(
                  config.getKeyStorePassword(), config.getKeyStorePath(), config.getCrlPath())
              : new SoftwareKeyStoreWrapper(
                  config.getKeyStoreType(),
                  config.getKeyStorePath(),
                  config.getKeyStorePassword(),
                  config.getTrustStoreType(),
                  config.getTrustStorePath(),
                  config.getTrustStorePassword(),
                  config.getCrlPath());
      ret =
          TLSContextFactory.getInstance(
              config.getKeyStorePassword(), wrapper, config.getAllowedProtocols());
    }
    return ret;
  }
}
