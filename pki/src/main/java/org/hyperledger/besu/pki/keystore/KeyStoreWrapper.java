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

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.util.Collection;

/** The interface Key store wrapper. */
public interface KeyStoreWrapper {

  /** The constant KEYSTORE_TYPE_JKS. */
  String KEYSTORE_TYPE_JKS = "JKS";
  /** The constant KEYSTORE_TYPE_PKCS11. */
  String KEYSTORE_TYPE_PKCS11 = "PKCS11";
  /** The constant KEYSTORE_TYPE_PKCS12. */
  String KEYSTORE_TYPE_PKCS12 = "PKCS12";

  /**
   * Gets key store.
   *
   * @return the key store
   */
  KeyStore getKeyStore();

  /**
   * Gets trust store.
   *
   * @return the trust store
   */
  KeyStore getTrustStore();

  /**
   * Gets private key.
   *
   * @param keyAlias the key alias
   * @return the private key
   */
  PrivateKey getPrivateKey(String keyAlias);

  /**
   * Gets public key.
   *
   * @param keyAlias the key alias
   * @return the public key
   */
  PublicKey getPublicKey(String keyAlias);

  /**
   * Gets certificate.
   *
   * @param certificateAlias the certificate alias
   * @return the certificate
   */
  Certificate getCertificate(String certificateAlias);

  /**
   * Get certificate chain array.
   *
   * @param certificateAlias the certificate alias
   * @return the certificate [ ]
   */
  Certificate[] getCertificateChain(String certificateAlias);

  /**
   * Gets CRLs.
   *
   * @return the CRLs
   */
  Collection<X509CRL> getCRLs();
}
