/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.pki.util.TestCertificateUtils.createCRL;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createKeyPair;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createSelfSignedCertificate;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.issueCertificate;

import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;
import org.hyperledger.besu.pki.util.TestCertificateUtils.Algorithm;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CmsTestKeystores {
  private KeyStore keystore;
  private KeyStore truststore;
  private List<X509CRL> CRLs;

  private KeyStoreWrapper keystoreWrapper;
  private KeyStoreWrapper truststoreWrapper;
  private KeyStoreWrapper truststoreWrapperWithoutCrl;
  private CmsValidator cmsValidator;
  private CmsValidator cmsValidatorWithoutCrl;

  public CmsTestKeystores(final Algorithm algorithm) {
    try {
      init(algorithm);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void init(final Algorithm algorithm) throws Exception {
    final Instant notBefore = Instant.now().minus(1, ChronoUnit.DAYS);
    final Instant notAfter = Instant.now().plus(1, ChronoUnit.DAYS);

    /*
     Create self-signed certificate
    */
    final KeyPair selfsignedKeyPair = createKeyPair(algorithm);
    final X509Certificate selfsignedCertificate =
        createSelfSignedCertificate("selfsigned", notBefore, notAfter, selfsignedKeyPair);

    /*
      Create trusted chain (ca -> interca -> partneraca -> partneravalidator)
    */
    final KeyPair caKeyPair = createKeyPair(algorithm);
    final X509Certificate caCertificate =
        createSelfSignedCertificate("ca", notBefore, notAfter, caKeyPair);

    final KeyPair interCAKeyPair = createKeyPair(algorithm);
    final X509Certificate interCACertificate =
        issueCertificate(
            caCertificate, caKeyPair, "interca", notBefore, notAfter, interCAKeyPair, true);

    final KeyPair partnerACAPair = createKeyPair(algorithm);
    final X509Certificate partnerACACertificate =
        issueCertificate(
            interCACertificate,
            interCAKeyPair,
            "partneraca",
            notBefore,
            notAfter,
            partnerACAPair,
            true);

    final KeyPair parterAValidatorKeyPair = createKeyPair(algorithm);
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
    final KeyPair expiredKeyPair = createKeyPair(algorithm);
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
    final KeyPair revokedKeyPair = createKeyPair(algorithm);
    final X509Certificate revokedCertificate =
        issueCertificate(
            caCertificate, caKeyPair, "revoked", notBefore, notAfter, revokedKeyPair, true);

    /*
     Create untrusted chain (untrusted_selfsigned -> unstrusted_partner)
    */
    final KeyPair untrustedSelfSignedKeyPair = createKeyPair(algorithm);
    final X509Certificate untrustedSelfsignedCertificate =
        createSelfSignedCertificate(
            "untrusted_selfsigned", notBefore, notAfter, untrustedSelfSignedKeyPair);

    final KeyPair untrustedIntermediateKeyPair = createKeyPair(algorithm);
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

    keystoreWrapper = new SoftwareKeyStoreWrapper(null, keystore, "");

    truststoreWrapper = new SoftwareKeyStoreWrapper(CRLs, truststore, "");

    truststoreWrapperWithoutCrl = new SoftwareKeyStoreWrapper(null, truststore, "");

    cmsValidator = new CmsValidator(truststoreWrapper);

    cmsValidatorWithoutCrl = new CmsValidator(truststoreWrapperWithoutCrl);
  }

  public KeyStore getKeystore() {
    return keystore;
  }

  public KeyStore getTruststore() {
    return truststore;
  }

  public List<X509CRL> getCRLs() {
    return CRLs;
  }

  public KeyStoreWrapper getKeystoreWrapper() {
    return keystoreWrapper;
  }

  public KeyStoreWrapper getTruststoreWrapper() {
    return truststoreWrapper;
  }

  public CmsValidator getCmsValidator() {
    return cmsValidator;
  }

  public KeyStoreWrapper getTruststoreWrapperWithoutCrl() {
    return truststoreWrapperWithoutCrl;
  }

  public CmsValidator getCmsValidatorWithoutCrl() {
    return cmsValidatorWithoutCrl;
  }
}
