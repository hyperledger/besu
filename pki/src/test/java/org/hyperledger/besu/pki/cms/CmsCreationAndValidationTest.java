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

import static org.hyperledger.besu.pki.util.TestCertificateUtils.Algorithm.EC;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.Algorithm.RSA;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.pki.util.TestCertificateUtils.Algorithm;

import java.security.PrivateKey;
import java.security.PublicKey;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class CmsCreationAndValidationTest {

  private static final CmsTestKeystores rsaTestKeystores = new CmsTestKeystores(RSA);
  private static final CmsTestKeystores ecTestKeystores = new CmsTestKeystores(EC);

  private CmsTestKeystores getCmsTestKeystores(final Algorithm algorithm) {
    return switch (algorithm) {
      case RSA -> rsaTestKeystores;
      case EC -> ecTestKeystores;
    };
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithEmptyCmsMessage(final Algorithm algorithm) {
    final Bytes data = Bytes.random(32);

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(Bytes.EMPTY, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithTrustedSelfSignedCertificate(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "trusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertTrue(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithUntrustedSelfSignedCertificate(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "untrusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithTrustedChain(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "trusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertTrue(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithUntrustedChain(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "untrusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithExpiredCertificate(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "expired");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithRevokedCertificate(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "revoked");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithoutCRLConfigDisablesCRLCheck(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "revoked");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    CmsValidator cmsValidator = getCmsTestKeystores(algorithm).getCmsValidatorWithoutCrl();

    // Because we don't have a CRL CertStore, revocation is not checked
    assertTrue(cmsValidator.validate(cms, data));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithWrongSignedData(final Algorithm algorithm) {
    final CmsCreator cmsCreator =
        new CmsCreator(getCmsTestKeystores(algorithm).getKeystoreWrapper(), "trusted");
    final Bytes otherData = Bytes.random(32);
    final Bytes cms = cmsCreator.create(otherData);

    final Bytes expectedData = Bytes.random(32);
    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cms, expectedData));
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class)
  public void cmsValidationWithInvalidSignature(final Algorithm algorithm) throws Exception {
    // Create a CMS message signed with a certificate, but create SignerInfo using another
    // certificate to trigger the signature verification to fail.

    final PrivateKey privateKey =
        getCmsTestKeystores(algorithm).getKeystoreWrapper().getPrivateKey("trusted");
    final PublicKey publicKey =
        getCmsTestKeystores(algorithm).getKeystoreWrapper().getPublicKey("trusted");
    final X509Certificate signerCertificate =
        (X509Certificate)
            getCmsTestKeystores(algorithm).getKeystoreWrapper().getCertificate("trusted");
    final X509Certificate otherCertificate =
        (X509Certificate)
            getCmsTestKeystores(algorithm)
                .getKeystoreWrapper()
                .getCertificate("trusted_selfsigned");

    final ContentSigner contentSigner =
        new JcaContentSignerBuilder(CmsCreator.getPreferredSignatureAlgorithm(publicKey))
            .build(privateKey);

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

    assertFalse(getCmsTestKeystores(algorithm).getCmsValidator().validate(cmsBytes, expectedData));
  }
}
