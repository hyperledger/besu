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

package org.hyperledger.besu.pki.cms;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.pki.crl.CRLUtil;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.nio.file.Paths;
import java.security.cert.CertStore;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class CmsCreationAndValidationTest {

  private static final String PATH_TO_KEYSTORES = "src/test/resources/cms/";
  private static final String KEYSTORE_TYPE = "PKCS12";
  private static final String KEYSTORE_PASSWORD = "validator";

  private KeyStoreWrapper keystore;
  private KeyStoreWrapper truststore;
  private CmsValidator cmsValidator;

  @Before
  public void before() {
    truststore = loadKeystore("truststore");
    keystore = loadKeystore("keystore");
    cmsValidator = new CmsValidator(truststore, loadCRLs("crl.pem"));
  }

  @Test
  public void cmsValidationWithTrustedSelfSignedCertificate() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "trusted_selfsigned");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithUntrustedSelfSignedCertificate() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "untrusted_selfsigned");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithValidChain() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "trusted");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithInvalidChain() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "untrusted");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithExpiredCertificate() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "expired");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithRevokedCertificate() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "revoked");
    Bytes data = Bytes.random(32);

    Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithWrongSignedData() {
    CmsCreator cmsCreator = new CmsCreator(keystore, "trusted");
    Bytes otherData = Bytes.random(32);
    Bytes expectedData = Bytes.random(32);

    Bytes cms = cmsCreator.create(otherData);

    assertThat(cmsValidator.validate(cms, expectedData)).isFalse();
  }

  private KeyStoreWrapper loadKeystore(final String name) {
    return new SoftwareKeyStoreWrapper(
        KEYSTORE_TYPE, Paths.get(PATH_TO_KEYSTORES, name), KEYSTORE_PASSWORD);
  }

  private CertStore loadCRLs(final String name) {
    return CRLUtil.loadCRLs(PATH_TO_KEYSTORES + "/" + name);
  }
}
