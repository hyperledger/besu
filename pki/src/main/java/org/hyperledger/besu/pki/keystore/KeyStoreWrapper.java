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

public interface KeyStoreWrapper {

  String KEYSTORE_TYPE_JKS = "JKS";
  String KEYSTORE_TYPE_PKCS11 = "PKCS11";
  String KEYSTORE_TYPE_PKCS12 = "PKCS12";

  KeyStore getKeyStore();

  KeyStore getTrustStore();

  PrivateKey getPrivateKey(String keyAlias);

  PublicKey getPublicKey(String keyAlias);

  Certificate getCertificate(String certificateAlias);

  Certificate[] getCertificateChain(String certificateAlias);

  Collection<X509CRL> getCRLs();
}
