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

import java.nio.file.Path;
import java.security.cert.Certificate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class BaseKeyStoreFileWrapperTest {
  protected static final String KEYSTORE_VALID_KEY_ALIAS = "partner1client1";
  protected static final String KEYSTORE_INVALID_KEY_ALIAS = "partner1clientinvalid";
  protected static final String TRUSTSTORE_VALID_CERTIFICATE_ALIAS = "interca";
  protected static final String TRUSTSTORE_INVALID_CERTIFICATE_ALIAS = "interca-invalid";

  @Parameterized.Parameter public String keyStoreWrapperDescription;

  @Parameterized.Parameter(1)
  public boolean keystoreWrapperConfiguredWithTruststore;

  @Parameterized.Parameter(2)
  public KeyStoreWrapper keyStoreWrapper;

  protected static Path toPath(final String path) throws Exception {
    return null == path
        ? null
        : Path.of(BaseKeyStoreFileWrapperTest.class.getResource(path).toURI());
  }

  @Test
  public void getPublicKey_WithValidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getPublicKey(KEYSTORE_VALID_KEY_ALIAS))
        .as("Public key is not null")
        .isNotNull();
  }

  @Test
  public void getPublicKey_WithInvalidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getPublicKey(KEYSTORE_INVALID_KEY_ALIAS))
        .as("Public key is null")
        .isNull();
  }

  @Test
  public void getPrivateKey_WithValidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getPrivateKey(KEYSTORE_VALID_KEY_ALIAS))
        .as("Private key is not null")
        .isNotNull();
  }

  @Test
  public void getPrivateKey_WithInvalidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getPrivateKey(KEYSTORE_INVALID_KEY_ALIAS))
        .as("Private key is null")
        .isNull();
  }

  @Test
  public void getCertificate_WithValidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getCertificate(KEYSTORE_VALID_KEY_ALIAS))
        .as("Certificate is not null")
        .isNotNull();
  }

  @Test
  public void getCertificate_WithInvalidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getCertificate(KEYSTORE_INVALID_KEY_ALIAS))
        .as("Certificate is null")
        .isNull();
  }

  @Test
  public void getCertificateChain_WithValidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getCertificateChain(KEYSTORE_VALID_KEY_ALIAS))
        .as("Certificate chain is not null")
        .isNotNull();
  }

  @Test
  public void getCertificateChain_WithInvalidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getCertificateChain(KEYSTORE_INVALID_KEY_ALIAS))
        .as("Certificate is null")
        .isNull();
  }

  @Test
  public void getCertificate_FromTruststore_WithValidAlias_ReturnsExpectedValue() {
    final Certificate certificate =
        keyStoreWrapper.getCertificate(TRUSTSTORE_VALID_CERTIFICATE_ALIAS);
    if (keystoreWrapperConfiguredWithTruststore) {
      assertThat(certificate).as("Certificate is not null").isNotNull();
    } else {
      assertThat(certificate).as("Certificate is null").isNull();
    }
  }

  @Test
  public void getCertificate_FromTruststore_WithInvalidAlias_ReturnsExpectedValue() {
    assertThat(keyStoreWrapper.getPrivateKey(TRUSTSTORE_INVALID_CERTIFICATE_ALIAS))
        .as("Certificate is null")
        .isNull();
  }

  @Test
  public void getCRLS_Check() {
    assertThat(keyStoreWrapper.getCRLs()).as("CRLs is not null").isNotNull();
    assertThat(keyStoreWrapper.getCRLs().size()).as("CRLs size matches").isEqualTo(2);
  }
}
