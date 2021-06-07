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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.pki.keystore.KeyStoreWrapper.KEYSTORE_TYPE_PKCS12;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SoftwareKeyStoreWrapperTest {

  private static final String KEY_ALIAS = "keyalias";
  private static final String CERTIFICATE_ALIAS = "certalias";
  private static final char[] PASSWORD = "password".toCharArray();

  private SoftwareKeyStoreWrapper keyStoreWrapper;

  @Mock private KeyStore keyStore;
  @Mock private KeyStore trustStore;
  @Mock private PrivateKey privateKey;
  @Mock private PublicKey publicKey;
  @Mock private Certificate certificate;

  @Before
  public void before() {
    keyStoreWrapper = new SoftwareKeyStoreWrapper(keyStore, new String(PASSWORD), null, "");
  }

  @Test
  public void getPrivateKey() throws Exception {
    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(keyStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(privateKey);

    assertThat(keyStoreWrapper.getPrivateKey(KEY_ALIAS)).isNotNull();
  }

  @Test
  public void getPrivateKeyCaching() throws Exception {
    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(keyStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(privateKey);

    keyStoreWrapper.getPrivateKey(KEY_ALIAS);
    keyStoreWrapper.getPrivateKey(KEY_ALIAS);

    verify(keyStore, times(1)).getKey(eq(KEY_ALIAS), eq(PASSWORD));
  }

  @Test
  public void getPrivateKeyFallbackToTrustStore() throws Exception {
    keyStoreWrapper =
        new SoftwareKeyStoreWrapper(
            keyStore, new String(PASSWORD), trustStore, new String(PASSWORD));

    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(false);
    when(trustStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(trustStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(privateKey);

    assertThat(keyStoreWrapper.getPrivateKey(KEY_ALIAS)).isNotNull();

    verify(trustStore).getKey(eq(KEY_ALIAS), eq(PASSWORD));
  }

  @Test
  public void getPublicKey() throws Exception {
    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(keyStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(publicKey);

    assertThat(keyStoreWrapper.getPublicKey(KEY_ALIAS)).isNotNull();
  }

  @Test
  public void getPublicKeyCaching() throws Exception {
    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(keyStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(publicKey);

    keyStoreWrapper.getPublicKey(KEY_ALIAS);
    keyStoreWrapper.getPublicKey(KEY_ALIAS);

    verify(keyStore, times(1)).getKey(eq(KEY_ALIAS), eq(PASSWORD));
  }

  @Test
  public void getPublicKeyFallbackToTrustStore() throws Exception {
    keyStoreWrapper =
        new SoftwareKeyStoreWrapper(
            keyStore, new String(PASSWORD), trustStore, new String(PASSWORD));

    when(keyStore.containsAlias(KEY_ALIAS)).thenReturn(false);
    when(trustStore.containsAlias(KEY_ALIAS)).thenReturn(true);
    when(trustStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(publicKey);

    assertThat(keyStoreWrapper.getPublicKey(KEY_ALIAS)).isNotNull();

    verify(trustStore).getKey(eq(KEY_ALIAS), eq(PASSWORD));
  }

  @Test
  public void getCertificate() throws Exception {
    when(keyStore.getCertificate(CERTIFICATE_ALIAS)).thenReturn(certificate);

    assertThat(keyStoreWrapper.getCertificate(CERTIFICATE_ALIAS)).isNotNull();
  }

  @Test
  public void getCertificateCaching() throws Exception {
    when(keyStore.getCertificate(CERTIFICATE_ALIAS)).thenReturn(certificate);

    keyStoreWrapper.getCertificate(CERTIFICATE_ALIAS);
    keyStoreWrapper.getCertificate(CERTIFICATE_ALIAS);

    verify(keyStore, times(1)).getCertificate(eq(CERTIFICATE_ALIAS));
  }

  @Test
  public void getCertificateFallbackToTrustStore() throws Exception {
    keyStoreWrapper =
        new SoftwareKeyStoreWrapper(
            keyStore, new String(PASSWORD), trustStore, new String(PASSWORD));

    when(keyStore.getCertificate(CERTIFICATE_ALIAS)).thenReturn(null);
    when(trustStore.getCertificate(CERTIFICATE_ALIAS)).thenReturn(certificate);

    assertThat(keyStoreWrapper.getCertificate(CERTIFICATE_ALIAS)).isNotNull();

    verify(trustStore).getCertificate(eq(CERTIFICATE_ALIAS));
  }

  @Test
  public void getCertificateChain() throws Exception {
    when(keyStore.getCertificateChain(CERTIFICATE_ALIAS))
        .thenReturn(new Certificate[] {certificate});

    assertThat(keyStoreWrapper.getCertificateChain(CERTIFICATE_ALIAS)).hasSize(1);
  }

  @Test
  public void getCertificateChainFallbackToTrustStore() throws Exception {
    keyStoreWrapper =
        new SoftwareKeyStoreWrapper(
            keyStore, new String(PASSWORD), trustStore, new String(PASSWORD));

    when(keyStore.getCertificateChain(CERTIFICATE_ALIAS)).thenReturn(null);
    when(trustStore.getCertificateChain(CERTIFICATE_ALIAS))
        .thenReturn(new Certificate[] {certificate});

    assertThat(keyStoreWrapper.getCertificateChain(CERTIFICATE_ALIAS)).hasSize(1);

    verify(trustStore).getCertificateChain(eq(CERTIFICATE_ALIAS));
  }

  @Test
  public void loadKeyStoreFromFile() {
    SoftwareKeyStoreWrapper loadedKeyStore =
        new SoftwareKeyStoreWrapper(
            KEYSTORE_TYPE_PKCS12,
            Path.of("src/test/resources/keystore/keystore"),
            "validator",
            KEYSTORE_TYPE_PKCS12,
            Path.of("src/test/resources/keystore/keystore"),
            "validator",
            null);

    assertThat(loadedKeyStore.getPublicKey("validator")).isNotNull();
    assertThat(loadedKeyStore.getPrivateKey("validator")).isNotNull();
    assertThat(loadedKeyStore.getCertificate("validator")).isNotNull();
    // CA -> INTERCA -> PARTNERACA -> VALIDATOR
    assertThat(loadedKeyStore.getCertificateChain("validator")).hasSize(4);
  }
}
