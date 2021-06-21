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

package org.hyperledger.besu.pki.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CRLReason;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Random;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/*
 This class provides utility method for creating certificates used on tests.

 Based on https://stackoverflow.com/a/18648284/5021783
*/
public class TestCertificateUtils {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  public static KeyPair createKeyPair() {
    try {
      final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      return kpg.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Error creating KeyPair", e);
    }
  }

  public static X509Certificate createSelfSignedCertificate(
      final String name, final Instant notBefore, final Instant notAfter, final KeyPair keyPair) {
    try {
      final ContentSigner signer =
          new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());

      final X509v3CertificateBuilder certificateBuilder =
          new JcaX509v3CertificateBuilder(
                  new X500Name("CN=" + name),
                  new BigInteger(32, new Random()),
                  Date.from(notBefore),
                  Date.from(notAfter),
                  new X500Name("CN=" + name),
                  keyPair.getPublic())
              .addExtension(
                  Extension.authorityKeyIdentifier,
                  false,
                  new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(keyPair.getPublic()))
              .addExtension(
                  Extension.subjectKeyIdentifier,
                  false,
                  new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic()))
              .addExtension(
                  Extension.basicConstraints,
                  false,
                  new BasicConstraints(true)) // true if it is allowed to sign other certs
              .addExtension(
                  Extension.keyUsage,
                  true,
                  new X509KeyUsage(X509KeyUsage.keyCertSign | X509KeyUsage.cRLSign));

      final X509CertificateHolder certHolder = certificateBuilder.build(signer);

      return new JcaX509CertificateConverter()
          .setProvider(BouncyCastleProvider.PROVIDER_NAME)
          .getCertificate(certHolder);

    } catch (final Exception e) {
      throw new RuntimeException("Error creating CA certificate", e);
    }
  }

  public static X509Certificate issueCertificate(
      final X509Certificate issuer,
      final KeyPair issuerKeyPair,
      final String subject,
      final Instant notBefore,
      final Instant notAfter,
      final KeyPair keyPair,
      final boolean isCa) {

    try {
      final ContentSigner signer =
          new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerKeyPair.getPrivate());

      final X509v3CertificateBuilder certificateBuilder =
          new JcaX509v3CertificateBuilder(
                  issuer,
                  new BigInteger(32, new Random()),
                  Date.from(notBefore),
                  Date.from(notAfter),
                  new X500Name("CN=" + subject),
                  keyPair.getPublic())
              .addExtension(
                  Extension.authorityKeyIdentifier,
                  false,
                  new JcaX509ExtensionUtils()
                      .createAuthorityKeyIdentifier(issuerKeyPair.getPublic()))
              .addExtension(
                  Extension.basicConstraints,
                  false,
                  new BasicConstraints(isCa)) // true if it is allowed to sign other certs
              .addExtension(
                  Extension.keyUsage,
                  true,
                  new X509KeyUsage(
                      X509KeyUsage.digitalSignature
                          | X509KeyUsage.nonRepudiation
                          | X509KeyUsage.keyEncipherment
                          | X509KeyUsage.dataEncipherment
                          | X509KeyUsage.cRLSign
                          | X509KeyUsage.keyCertSign));

      final X509CertificateHolder certHolder = certificateBuilder.build(signer);

      return new JcaX509CertificateConverter()
          .setProvider(BouncyCastleProvider.PROVIDER_NAME)
          .getCertificate(certHolder);
    } catch (final Exception e) {
      throw new RuntimeException("Error creating certificate", e);
    }
  }

  public static X509CRL createCRL(
      final X509Certificate issuer,
      final KeyPair issuerKeyPair,
      final Collection<X509Certificate> revokedCertificates) {
    try {
      final X509CertificateHolder x509CertificateHolder =
          new X509CertificateHolder(issuer.getEncoded());

      final X509v2CRLBuilder crlBuilder =
          new X509v2CRLBuilder(x509CertificateHolder.getSubject(), Date.from(Instant.now()));

      revokedCertificates.forEach(
          c ->
              crlBuilder.addCRLEntry(
                  c.getSerialNumber(), Date.from(Instant.now()), CRLReason.UNSPECIFIED.ordinal()));

      crlBuilder.setNextUpdate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));

      final ContentSigner signer =
          new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerKeyPair.getPrivate());
      final X509CRLHolder crlHolder = crlBuilder.build(signer);
      return new JcaX509CRLConverter()
          .setProvider(BouncyCastleProvider.PROVIDER_NAME)
          .getCRL(crlHolder);
    } catch (OperatorCreationException
        | CRLException
        | CertificateEncodingException
        | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
