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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.security.cert.Certificate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BaseKeyStoreFileWrapperTest {
  protected static final String KEYSTORE_VALID_KEY_ALIAS = "partner1client1";
  protected static final String KEYSTORE_INVALID_KEY_ALIAS = "partner1clientinvalid";
  protected static final String TRUSTSTORE_VALID_CERTIFICATE_ALIAS = "interca";
  protected static final String TRUSTSTORE_INVALID_CERTIFICATE_ALIAS = "interca-invalid";

  protected static Path toPath(final String path) throws Exception {
    return null == path
        ? null
        : Path.of(BaseKeyStoreFileWrapperTest.class.getResource(path).toURI());
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getPublicKey_WithValidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNotNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getPublicKey(KEYSTORE_VALID_KEY_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getPublicKey_WithInvalidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getPublicKey(KEYSTORE_INVALID_KEY_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getPrivateKey_WithValidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNotNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getPrivateKey(KEYSTORE_VALID_KEY_ALIAS),
        "Private key is not null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getPrivateKey_WithInvalidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getPrivateKey(KEYSTORE_INVALID_KEY_ALIAS),
        "Private key is null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificate_WithValidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNotNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getCertificate(KEYSTORE_VALID_KEY_ALIAS),
        "Certificate is not null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificate_WithInvalidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getCertificate(KEYSTORE_INVALID_KEY_ALIAS),
        "Certificate is null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificateChain_WithValidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNotNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getCertificateChain(KEYSTORE_VALID_KEY_ALIAS),
        "Certificate chain is not null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificateChain_WithInvalidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getCertificateChain(
            KEYSTORE_INVALID_KEY_ALIAS),
        "Certificate is null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificate_FromTruststore_WithValidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    final Certificate certificate =
        keyStoreWrapperTestParameter.keyStoreWrapper.getCertificate(
            TRUSTSTORE_VALID_CERTIFICATE_ALIAS);
    if (keyStoreWrapperTestParameter.keystoreWrapperConfiguredWithTruststore) {
      assertNotNull(certificate, "Certificate is not null");
    } else {
      assertNull(certificate, "Certificate is null");
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCertificate_FromTruststore_WithInvalidAlias_ReturnsExpectedValue(
      final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNull(
        keyStoreWrapperTestParameter.keyStoreWrapper.getPrivateKey(
            TRUSTSTORE_INVALID_CERTIFICATE_ALIAS),
        "Certificate is null");
  }

  @ParameterizedTest
  @MethodSource("data")
  public void getCRLS_Check(final KeyStoreWrapperTestParameter keyStoreWrapperTestParameter) {
    assertNotNull(keyStoreWrapperTestParameter.keyStoreWrapper.getCRLs(), "CRLs is not null");
    assertEquals(
        keyStoreWrapperTestParameter.keyStoreWrapper.getCRLs().size(), 2, "CRLs size matches");
  }

  public static class KeyStoreWrapperTestParameter {
    public String keyStoreWrapperDescription;
    public boolean keystoreWrapperConfiguredWithTruststore;
    public KeyStoreWrapper keyStoreWrapper;

    public KeyStoreWrapperTestParameter(
        final String keyStoreWrapperDescription,
        final boolean keystoreWrapperConfiguredWithTruststore,
        final KeyStoreWrapper keyStoreWrapper) {
      this.keyStoreWrapperDescription = keyStoreWrapperDescription;
      this.keystoreWrapperConfiguredWithTruststore = keystoreWrapperConfiguredWithTruststore;
      this.keyStoreWrapper = keyStoreWrapper;
    }
  }
}
