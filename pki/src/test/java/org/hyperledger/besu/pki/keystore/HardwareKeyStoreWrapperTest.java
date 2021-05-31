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
import static org.mockito.Mockito.when;

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
public class HardwareKeyStoreWrapperTest {

  private static final String KEY_ALIAS = "keyalias";
  private static final String CERTIFICATE_ALIAS = "certalias";
  private static final char[] PASSWORD = "password".toCharArray();

  @Mock private KeyStore keyStore;
  @Mock private PrivateKey privateKey;
  @Mock private PublicKey publicKey;
  @Mock private Certificate certificate;

  private HardwareKeyStoreWrapper keyStoreWrapper;

  @Before
  public void before() {
    keyStoreWrapper = new HardwareKeyStoreWrapper(keyStore, new String(PASSWORD));
  }

  @Test
  public void getPrivateKey() throws Exception {
    when(keyStore.getKey(KEY_ALIAS, PASSWORD)).thenReturn(privateKey);

    assertThat(keyStoreWrapper.getPrivateKey(KEY_ALIAS)).isNotNull();
  }

  @Test
  public void getPublicKey() throws Exception {
    // Get public key from certificate
    when(keyStore.getCertificate(KEY_ALIAS)).thenReturn(certificate);
    when(certificate.getPublicKey()).thenReturn(publicKey);

    assertThat(keyStoreWrapper.getPublicKey(KEY_ALIAS)).isNotNull();
  }

  @Test
  public void getCertificate() throws Exception {
    when(keyStore.getCertificate(CERTIFICATE_ALIAS)).thenReturn(certificate);

    assertThat(keyStoreWrapper.getCertificate(CERTIFICATE_ALIAS)).isNotNull();
  }

  @Test
  public void getCertificateChain() throws Exception {
    when(keyStore.getCertificateChain(CERTIFICATE_ALIAS))
        .thenReturn(new Certificate[] {certificate});

    assertThat(keyStoreWrapper.getCertificateChain(CERTIFICATE_ALIAS)).hasSize(1);
  }
}
