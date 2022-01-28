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
package org.hyperledger.besu.pki.keystore;

import org.hyperledger.besu.pki.PkiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an instance of this class which is backed by a PKCS#11 keystore, such as a software
 * (emulated) HSM or a physical/cloud HSM (see <a href=
 * "https://docs.oracle.com/en/java/javase/11/security/pkcs11-reference-guide1.html">here</a>
 */
public class HardwareKeyStoreWrapper extends AbstractKeyStoreWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(HardwareKeyStoreWrapper.class);

  private static final String pkcs11Provider = "SunPKCS11";

  private final KeyStore keystore;
  private final transient char[] keystorePassword;

  private final java.security.Provider provider;

  public HardwareKeyStoreWrapper(
      final String keystorePassword, final Provider provider, final Path crlLocation) {
    super(crlLocation);
    try {
      if (provider == null) {
        throw new IllegalArgumentException("Provider is null");
      }
      this.keystorePassword = keystorePassword.toCharArray();

      this.provider = provider;
      if (Security.getProvider(provider.getName()) == null) {
        Security.addProvider(provider);
      }

      keystore = KeyStore.getInstance(KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, provider);
      keystore.load(null, this.keystorePassword);

    } catch (final Exception e) {
      throw new PkiException("Failed to initialize HSM keystore", e);
    }
  }

  public HardwareKeyStoreWrapper(
      final String keystorePassword, final Path config, final Path crlLocation) {
    super(crlLocation);
    try {
      if (keystorePassword == null) {
        throw new IllegalArgumentException("Keystore password is null");
      }
      final Properties properties = new Properties();
      final File configFile = config.toFile();
      try (InputStream ins = new FileInputStream(configFile)) {
        properties.load(ins);
      }
      final String name = properties.getProperty("name");
      this.keystorePassword = keystorePassword.toCharArray();
      final Optional<Provider> existingProvider =
          Stream.of(Security.getProviders())
              .filter(p -> p.getName().equals(String.format("%s-%s", pkcs11Provider, name)))
              .findAny();
      if (existingProvider.isPresent()) {
        provider = existingProvider.get();
      } else {
        provider = getPkcs11Provider(configFile.getAbsolutePath());
        Security.addProvider(provider);
      }

      keystore = KeyStore.getInstance(KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, provider);
      keystore.load(null, this.keystorePassword);

    } catch (final Exception e) {
      throw new PkiException("Failed to initialize HSM keystore", e);
    }
  }

  @VisibleForTesting
  HardwareKeyStoreWrapper(final KeyStore keystore, final String password) {
    super(null);
    this.keystore = keystore;
    this.keystorePassword = password.toCharArray();
    this.provider = null;
  }

  @Override
  public PrivateKey getPrivateKey(final String keyAlias) {
    try {
      LOG.debug("Retrieving private key for alias: {}", keyAlias);
      return (PrivateKey) keystore.getKey(keyAlias, this.keystorePassword);
    } catch (final Exception e) {
      throw new PkiException("Failed to get key: " + keyAlias, e);
    }
  }

  @Override
  public PublicKey getPublicKey(final String keyAlias) {
    try {
      LOG.debug("Retrieving public key for alias: {}", keyAlias);
      final Certificate certificate = keystore.getCertificate(keyAlias);
      return (certificate != null) ? certificate.getPublicKey() : null;
    } catch (final Exception e) {
      throw new PkiException("Failed to get key: " + keyAlias, e);
    }
  }

  @Override
  public Certificate getCertificate(final String certificateAlias) {
    try {
      LOG.debug("Retrieving certificate for alias: {}", certificateAlias);
      return keystore.getCertificate(certificateAlias);
    } catch (final Exception e) {
      throw new PkiException("Failed to get certificate: " + certificateAlias, e);
    }
  }

  @Override
  public Certificate[] getCertificateChain(final String certificateAlias) {
    try {
      LOG.debug("Retrieving certificate chain for alias: {}", certificateAlias);
      return keystore.getCertificateChain(certificateAlias);
    } catch (final Exception e) {
      throw new PkiException("Failed to certificate chain for alias: " + certificateAlias, e);
    }
  }

  @Override
  public KeyStore getKeyStore() {
    return keystore;
  }

  @Override
  public KeyStore getTrustStore() {
    return keystore;
  }

  private Provider getPkcs11Provider(final String config) {
    final Provider provider = Security.getProvider(pkcs11Provider);
    if (null == provider) {
      throw new IllegalArgumentException("Unable to load PKCS11 provider configuration.");
    } else {
      return provider.configure(config);
    }
  }

  @VisibleForTesting
  public Provider getPkcs11ProviderForConfig(final String config) {
    return getPkcs11Provider(config);
  }
}
