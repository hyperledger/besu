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
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createCRL;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createKeyPair;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createSelfSignedCertificate;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.issueCertificate;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
import org.junit.BeforeClass;
import org.junit.Test;

public class CmsCreationAndValidationTest {

  private static KeyStore keystore;
  private static KeyStore truststore;
  private static List<X509CRL> CRLs;

  private KeyStoreWrapper keystoreWrapper;
  private KeyStoreWrapper truststoreWrapper;
  private CmsValidator cmsValidator;

  @BeforeClass
  public static void beforeAll() throws Exception {
    final Instant notBefore = Instant.now().minus(1, ChronoUnit.DAYS);
    final Instant notAfter = Instant.now().plus(1, ChronoUnit.DAYS);

    /*
     Create self-signed certificate
    */
    final KeyPair selfsignedKeyPair = createKeyPair();
    final X509Certificate selfsignedCertificate =
        createSelfSignedCertificate("selfsigned", notBefore, notAfter, selfsignedKeyPair);

    /*
      Create trusted chain (ca -> interca -> partneraca -> partneravalidator)
    */
    final KeyPair caKeyPair = createKeyPair();
    final X509Certificate caCertificate =
        createSelfSignedCertificate("ca", notBefore, notAfter, caKeyPair);

    final KeyPair interCAKeyPair = createKeyPair();
    final X509Certificate interCACertificate =
        issueCertificate(
            caCertificate, caKeyPair, "interca", notBefore, notAfter, interCAKeyPair, true);

    final KeyPair partnerACAPair = createKeyPair();
    final X509Certificate partnerACACertificate =
        issueCertificate(
            interCACertificate,
            interCAKeyPair,
            "partneraca",
            notBefore,
            notAfter,
            partnerACAPair,
            true);

    final KeyPair parterAValidatorKeyPair = createKeyPair();
    final X509Certificate partnerAValidatorCertificate =
        issueCertificate(
            partnerACACertificate,
            partnerACAPair,
            "partneravalidator",
            notBefore,
            notAfter,
            parterAValidatorKeyPair,
            false);

    /*
     Create expired certificate
    */
    final KeyPair expiredKeyPair = createKeyPair();
    final X509Certificate expiredCertificate =
        issueCertificate(
            caCertificate,
            caKeyPair,
            "expired",
            notBefore,
            notBefore.plus(1, ChronoUnit.SECONDS),
            expiredKeyPair,
            true);

    /*
     Create revoked and revoked certificates
    */
    final KeyPair revokedKeyPair = createKeyPair();
    final X509Certificate revokedCertificate =
        issueCertificate(
            caCertificate, caKeyPair, "revoked", notBefore, notAfter, revokedKeyPair, true);

    /*
     Create untrusted chain (untrusted_selfsigned -> unstrusted_partner)
    */
    final KeyPair untrustedSelfSignedKeyPair = createKeyPair();
    final X509Certificate untrustedSelfsignedCertificate =
        createSelfSignedCertificate(
            "untrusted_selfsigned", notBefore, notAfter, untrustedSelfSignedKeyPair);

    final KeyPair untrustedIntermediateKeyPair = createKeyPair();
    final X509Certificate untrustedIntermediateCertificate =
        issueCertificate(
            untrustedSelfsignedCertificate,
            untrustedSelfSignedKeyPair,
            "unstrusted_partner",
            notBefore,
            notAfter,
            untrustedIntermediateKeyPair,
            true);

    /*
     Create truststore wrapper with 3 trusted certificates: 'ca', 'interca' and 'selfsigned'
    */
    truststore = KeyStore.getInstance("PKCS12");
    truststore.load(null, null);

    truststore.setCertificateEntry("ca", caCertificate);
    truststore.setCertificateEntry("interca", interCACertificate);
    truststore.setCertificateEntry("selfsigned", selfsignedCertificate);

    /*
     Create keystore with certificates used in tests
    */
    keystore = KeyStore.getInstance("PKCS12");
    keystore.load(null, null);

    keystore.setKeyEntry(
        "trusted_selfsigned",
        selfsignedKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {selfsignedCertificate});
    keystore.setKeyEntry(
        "untrusted_selfsigned",
        untrustedSelfSignedKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {untrustedSelfsignedCertificate});
    keystore.setKeyEntry(
        "expired",
        expiredKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {expiredCertificate});
    keystore.setKeyEntry(
        "revoked",
        revokedKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {revokedCertificate});
    keystore.setKeyEntry(
        "trusted",
        parterAValidatorKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {partnerAValidatorCertificate, partnerACACertificate});
    keystore.setKeyEntry(
        "untrusted",
        untrustedIntermediateKeyPair.getPrivate(),
        "".toCharArray(),
        new Certificate[] {untrustedIntermediateCertificate, untrustedSelfsignedCertificate});

    /*
     Create CRLs for all CA certificates (mostly empty, only ca has one revoked certificate)
    */
    final X509CRL caCRL = createCRL(caCertificate, caKeyPair, Set.of(revokedCertificate));
    final X509CRL intercaCRL =
        createCRL(interCACertificate, interCAKeyPair, Collections.emptyList());
    final X509CRL partnerACACRL =
        createCRL(partnerACACertificate, partnerACAPair, Collections.emptyList());
    final X509CRL selfsignedCRL =
        createCRL(selfsignedCertificate, selfsignedKeyPair, Collections.emptyList());
    CRLs = List.of(caCRL, intercaCRL, partnerACACRL, selfsignedCRL);
  }

  @Before
  public void before() {
    keystoreWrapper = new SoftwareKeyStoreWrapper(keystore, "");

    truststoreWrapper = spy(new SoftwareKeyStoreWrapper(truststore, ""));
    when(truststoreWrapper.getCRLs()).thenReturn(CRLs);

    cmsValidator = new CmsValidator(truststoreWrapper);
  }

  @Test
  public void cmsValidationWithEmptyCmsMessage() {
    final Bytes data = Bytes.random(32);

    assertThat(cmsValidator.validate(Bytes.EMPTY, data)).isFalse();
  }

  @Test
  public void cmsValidationWithTrustedSelfSignedCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "trusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithUntrustedSelfSignedCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "untrusted_selfsigned");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithTrustedChain() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "trusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithUntrustedChain() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "untrusted");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithExpiredCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "expired");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithRevokedCertificate() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "revoked");
    final Bytes data = Bytes.random(32);

    final Bytes cms = cmsCreator.create(data);

    assertThat(cmsValidator.validate(cms, data)).isFalse();
  }

  @Test
  public void cmsValidationWithoutCRLConfigDisablesCRLCheck() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "revoked");
    final Bytes data = Bytes.random(32);

    // Removing CRLs
    when(truststoreWrapper.getCRLs()).thenReturn(null);

    final Bytes cms = cmsCreator.create(data);

    // Overriding validator with instance without CRL CertStore
    cmsValidator = new CmsValidator(truststoreWrapper);

    // Because we don't have a CRL CertStore, revocation is not checked
    assertThat(cmsValidator.validate(cms, data)).isTrue();
  }

  @Test
  public void cmsValidationWithWrongSignedData() {
    final CmsCreator cmsCreator = new CmsCreator(keystoreWrapper, "trusted");
    final Bytes otherData = Bytes.random(32);
    final Bytes cms = cmsCreator.create(otherData);

    final Bytes expectedData = Bytes.random(32);
    assertThat(cmsValidator.validate(cms, expectedData)).isFalse();
  }

  @Test
  public void cmsValidationWithInvalidSignature() throws Exception {
    // Create a CMS message signed with a certificate, but create SignerInfo using another
    // certificate to trigger the signature verification to fail.

    final PrivateKey privateKey = keystoreWrapper.getPrivateKey("trusted");
    final X509Certificate signerCertificate =
        (X509Certificate) keystoreWrapper.getCertificate("trusted");
    final X509Certificate otherCertificate =
        (X509Certificate) keystoreWrapper.getCertificate("trusted_selfsigned");

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
}
