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
import java.security.PrivateKey;
import java.security.cert.CertStore;
import java.security.cert.X509Certificate;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
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
    final CmsCreator cmsCreator = new CmsCreator(keystore, "trusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithUntrustedSelfSignedCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "untrusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithTrustedChain() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "trusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithUntrustedChain() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "untrusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithExpiredCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "expired");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithRevokedCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "revoked");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithoutCRLConfigDisablesCRLCheck() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "revoked");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    // Overriding validator with instance without CRL CertStore
    cmsValidator = new CmsValidator(truststore, null);

    // Because we don't have a CRL CertStore, revocation is not checked
    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithWrongSignedData() {
    final CmsCreator cmsCreator = new CmsCreator(keystore, "trusted");
    final Bytes otherData = Bytes.random(32);
    final Bytes cms = cmsCreator.create(otherData);

    final Bytes expectedData = Bytes.random(32);
    assertThat(cmsValidator.validate(cms, expectedData)).isFalse();
  }

  @Test
  public void cmsValidationWithInvalidSignature() throws Exception {
    // Create a CMS message signed with a certificate, but create SignerInfo using another
    // certificate to trigger the signature verification to fail.

    final PrivateKey privateKey = keystore.getPrivateKey("trusted");
    final X509Certificate signerCertificate = (X509Certificate) keystore.getCertificate("trusted");
    final X509Certificate otherCertificate =
        (X509Certificate) keystore.getCertificate("trusted_selfsigned");

    final ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);

    final CMSSignedDataGenerator cmsGenerator = new CMSSignedDataGenerator();
    cmsGenerator.addCertificate(new JcaX509CertificateHolder(signerCertificate));
    cmsGenerator.addCertificate(new JcaX509CertificateHolder(otherCertificate));

    final DigestCalculatorProvider digestCalculatorProvider =
        new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
    cmsGenerator.addSignerInfoGenerator(
        new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
            .build(contentSigner, otherCertificate));

    final Bytes expectedData = Bytes.random(32);
    final CMSTypedData cmsData = new CMSProcessableByteArray(expectedData.toArray());
    final CMSSignedData cmsSignedData = cmsGenerator.generate(cmsData, true);
    final Bytes cmsBytes = Bytes.wrap(cmsSignedData.getEncoded());

    assertThat(cmsValidator.validate(cmsBytes, expectedData)).isFalse();
  }

  private KeyStoreWrapper loadKeystore(final String name) {
    return new SoftwareKeyStoreWrapper(
        KEYSTORE_TYPE, Paths.get(PATH_TO_KEYSTORES, name), KEYSTORE_PASSWORD);
  }

  private CertStore loadCRLs(final String name) {
    return CRLUtil.loadCRLs(PATH_TO_KEYSTORES + "/" + name);
  }
}
