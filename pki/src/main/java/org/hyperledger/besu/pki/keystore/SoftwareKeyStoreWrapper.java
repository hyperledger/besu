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

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoftwareKeyStoreWrapper extends AbstractKeyStoreWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(SoftwareKeyStoreWrapper.class);

  private final KeyStore keystore;
  private final transient char[] keystorePassword;
  private KeyStore truststore;
  private transient char[] truststorePassword;

  private final Map<String, PrivateKey> cachedPrivateKeys = new HashMap<>();
  private final Map<String, PublicKey> cachedPublicKeys = new HashMap<>();
  private final Map<String, Certificate> cachedCertificates = new HashMap<>();

  public SoftwareKeyStoreWrapper(
      final String keystoreType,
      final Path keystoreLocation,
      final String keystorePassword,
      final Path crlLocation) {
    this(keystoreType, keystoreLocation, keystorePassword, null, null, null, crlLocation);
  }

  public SoftwareKeyStoreWrapper(
      final String keystoreType,
      final Path keystoreLocation,
      final String keystorePassword,
      final String truststoreType,
      final Path truststoreLocation,
      final String truststorePassword,
      final Path crlLocation) {
    super(crlLocation);

    if (keystorePassword == null) {
      throw new IllegalArgumentException("Keystore password is null");
    }
    this.keystorePassword = keystorePassword.toCharArray();
    try (InputStream stream = new FileInputStream(keystoreLocation.toFile())) {
      keystore = KeyStore.getInstance(keystoreType);
      keystore.load(stream, this.keystorePassword);

    } catch (final Exception e) {
      throw new PkiException("Failed to initialize software keystore: " + keystoreLocation, e);
    }

    if (truststoreType != null && truststoreLocation != null) {
      this.truststorePassword =
          (truststorePassword != null) ? truststorePassword.toCharArray() : null;
      try (InputStream stream = new FileInputStream(truststoreLocation.toFile())) {
        truststore = KeyStore.getInstance(truststoreType);
        truststore.load(stream, this.truststorePassword);

      } catch (final Exception e) {
        throw new PkiException(
            "Failed to initialize software truststore: " + truststoreLocation, e);
      }
    }
  }

  @VisibleForTesting
  public SoftwareKeyStoreWrapper(
      final KeyStore keystore,
      final String keystorePassword,
      final KeyStore truststore,
      final String truststorePassword) {
    super(null);
    this.keystore = keystore;
    this.keystorePassword = keystorePassword.toCharArray();
    this.truststore = truststore;
    this.truststorePassword = truststorePassword.toCharArray();
  }

  @VisibleForTesting
  public SoftwareKeyStoreWrapper(final KeyStore keystore, final String keystorePassword) {
    super(null);
    this.keystore = keystore;
    this.keystorePassword = keystorePassword.toCharArray();
    this.truststore = null;
    this.truststorePassword = null;
  }

  @Override
  public PrivateKey getPrivateKey(final String keyAlias) {
    LOG.debug("Retrieving private key for alias: {}", keyAlias);
    return (PrivateKey) getKey(keyAlias, PrivateKey.class, cachedPrivateKeys);
  }

  @Override
  public PublicKey getPublicKey(final String keyAlias) {
    LOG.debug("Retrieving public key for alias: {}", keyAlias);
    return (PublicKey) getKey(keyAlias, PublicKey.class, cachedPublicKeys);
  }

  @Override
  public Certificate getCertificate(final String certificateAlias) {
    try {
      LOG.debug("Retrieving certificate for alias: {}", certificateAlias);
      Certificate certificate = cachedCertificates.get(certificateAlias);
      if (certificate == null) {
        LOG.debug("Certificate alias: {} not cached", certificateAlias);

        certificate = keystore.getCertificate(certificateAlias);
        if (certificate == null && truststore != null) {
          certificate = truststore.getCertificate(certificateAlias);
        }
        if (certificate != null) {
          LOG.debug("Certificate alias: {} found in keystore/truststore", certificateAlias);
          cachedCertificates.put(certificateAlias, certificate);
          cachedPublicKeys.put(certificateAlias, certificate.getPublicKey());
          return certificate;
        } else {
          LOG.warn("Certificate alias: {} not found in keystore/truststore", certificateAlias);
        }
      }
      return certificate;

    } catch (final Exception e) {
      throw new PkiException("Failed to get certificate: " + certificateAlias, e);
    }
  }

  @Override
  public Certificate[] getCertificateChain(final String certificateAlias) {
    try {
      LOG.debug("Retrieving certificate chain for alias: {}", certificateAlias);

      Certificate[] certificateChain = keystore.getCertificateChain(certificateAlias);
      if (certificateChain == null && truststore != null) {
        certificateChain = truststore.getCertificateChain(certificateAlias);
      }
      return certificateChain;
    } catch (final Exception e) {
      throw new PkiException(
          "Failed to retrieve certificate chain for alias: " + certificateAlias, e);
    }
  }

  private Key getKey(
      final String keyAlias,
      final Class<? extends Key> keyTypeClass,
      final Map<String, ? extends Key> keyCache) {
    Key cachedKey = keyCache.get(keyAlias);
    if (cachedKey == null) {
      LOG.debug("Key alias: {} not cached", keyAlias);
      try {
        cachedKey = loadAndCacheKey(this.keystore, this.keystorePassword, keyAlias, keyTypeClass);
        if (cachedKey == null) {
          cachedKey =
              loadAndCacheKey(this.truststore, this.truststorePassword, keyAlias, keyTypeClass);
        }
      } catch (final Exception e) {
        throw new PkiException("Failed to get key: " + keyAlias, e);
      }
    }
    return cachedKey;
  }

  @Override
  public KeyStore getKeyStore() {
    return keystore;
  }

  @Override
  public KeyStore getTrustStore() {
    return truststore;
  }

  private Key loadAndCacheKey(
      final KeyStore keystore,
      final char[] keystorePassword,
      final String keyAlias,
      final Class<? extends Key> keyTypeClass)
      throws GeneralSecurityException {
    if (keystore != null && keystore.containsAlias(keyAlias)) {

      final Key key = keystore.getKey(keyAlias, keystorePassword);
      if (key != null) {
        LOG.debug("Key alias: {} found in keystore/truststore", keyAlias);
        if (key instanceof PrivateKey && PrivateKey.class.isAssignableFrom(keyTypeClass)) {
          cachedPrivateKeys.put(keyAlias, (PrivateKey) key);
          return key;
        } else if (key instanceof PublicKey && PublicKey.class.isAssignableFrom(keyTypeClass)) {
          cachedPublicKeys.put(keyAlias, (PublicKey) key);
          return key;
        }
      }

      if (PublicKey.class.isAssignableFrom(keyTypeClass)) {
        final Certificate certificate = getCertificate(keyAlias);
        if (certificate != null) {
          return certificate.getPublicKey();
        }
      }
    }

    LOG.warn("Key alias: {} not found in keystore/truststore", keyAlias);
    return null;
  }
}
