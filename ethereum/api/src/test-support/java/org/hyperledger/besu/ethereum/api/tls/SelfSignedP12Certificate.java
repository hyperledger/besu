/*
 *
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
 *
 */
package org.hyperledger.besu.ethereum.api.tls;

import static org.hyperledger.besu.crypto.SecureRandomProvider.createSecureRandom;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.Period;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.net.tls.TLS;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Utility class to generate temporary self-signed certificates in PKCS12 format for testing
 * purposes using BouncyCastle APIs. The generated certificate supports SAN extension for multiple
 * DNS and IP addresses
 *
 * <p>Note: DO NOT USE IN PRODUCTION. The generated stores and files are marked to be deleted on JVM
 * exit.
 */
public final class SelfSignedP12Certificate {
  private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
  private static final String alias = "test";
  private static final boolean IS_CA = true;
  private static final String distinguishedName = "CN=localhost";
  private static final List<String> sanHostNames = List.of("localhost");
  private static final List<String> sanIpAddresses = List.of("127.0.0.1");
  private static final char[] password = "changeit".toCharArray();
  private final Certificate certificate;
  private final Path keyStore;
  private final Path trustStore;

  private SelfSignedP12Certificate(
      final Certificate certificate, final Path keyStore, final Path trustStore) {
    this.certificate = certificate;
    this.keyStore = keyStore;
    this.trustStore = trustStore;
  }

  public static SelfSignedP12Certificate create() {
    try {
      final KeyPair keyPair = generateKeyPair();
      final Certificate certificate = generateSelfSignedCertificate(keyPair);
      final Path keyStore = createKeyStore(keyPair.getPrivate(), certificate);
      final Path trustStore = createTrustStore(certificate);
      return new SelfSignedP12Certificate(certificate, keyStore, trustStore);
    } catch (final IOException | GeneralSecurityException | OperatorCreationException e) {
      throw new RuntimeException("Error creating self signed certificates", e);
    }
  }

  public Certificate getCertificate() {
    return certificate;
  }

  public Path getKeyStoreFile() {
    return keyStore;
  }

  public Path getTrustStoreFile() {
    return trustStore;
  }

  public char[] getPassword() {
    return password;
  }

  public String getCommonName() {
    try {
      final X500Name subject = new X509CertificateHolder(certificate.getEncoded()).getSubject();
      final RDN commonNameRdn = subject.getRDNs(BCStyle.CN)[0];
      return IETFUtils.valueToString(commonNameRdn.getFirst().getValue());
    } catch (final IOException | CertificateEncodingException e) {
      throw new RuntimeException("Error extracting common name from certificate", e);
    }
  }

  public String getCertificateHexFingerprint() {
    try {
      return TLS.certificateHexFingerprint(certificate);
    } catch (CertificateEncodingException e) {
      throw new RuntimeException("Error extracting certificate fingerprint", e);
    }
  }

  private static KeyPair generateKeyPair() throws GeneralSecurityException {
    final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048, createSecureRandom());
    return keyPairGenerator.generateKeyPair();
  }

  @SuppressWarnings("JdkObsolete") // JcaX509v3CertificateBuilder requires java.util.Date.
  private static Certificate generateSelfSignedCertificate(final KeyPair keyPair)
      throws CertIOException, GeneralSecurityException, OperatorCreationException {
    final X500Name issuer = new X500Name(distinguishedName);
    final X500Name subject = new X500Name(distinguishedName);
    final BigInteger serialNumber = new BigInteger(String.valueOf(Instant.now().toEpochMilli()));
    final X509v3CertificateBuilder v3CertificateBuilder =
        new JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            Date.from(Instant.now()),
            Date.from(Instant.now().plus(Period.ofDays(90))),
            subject,
            keyPair.getPublic());

    // extensions
    v3CertificateBuilder.addExtension(
        Extension.basicConstraints, true, new BasicConstraints(IS_CA));
    v3CertificateBuilder.addExtension(
        Extension.subjectAlternativeName, false, getSubjectAlternativeNames());

    final ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());

    return new JcaX509CertificateConverter()
        .setProvider(BOUNCY_CASTLE_PROVIDER)
        .getCertificate(v3CertificateBuilder.build(contentSigner));
  }

  private static GeneralNames getSubjectAlternativeNames() {
    final List<GeneralName> hostGeneralNames =
        sanHostNames.stream()
            .map(hostName -> new GeneralName(GeneralName.dNSName, hostName))
            .collect(Collectors.toList());
    final List<GeneralName> ipGeneralNames =
        sanIpAddresses.stream()
            .map(ipAddress -> new GeneralName(GeneralName.iPAddress, ipAddress))
            .collect(Collectors.toList());
    final GeneralName[] generalNames =
        Stream.of(hostGeneralNames, ipGeneralNames)
            .flatMap(Collection::stream)
            .toArray(GeneralName[]::new);

    return new GeneralNames(generalNames);
  }

  private static Path createKeyStore(final PrivateKey privateKey, final Certificate certificate)
      throws IOException, GeneralSecurityException {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null);
    keyStore.setKeyEntry(alias, privateKey, password, new Certificate[] {certificate});
    return saveStore(keyStore);
  }

  private static Path createTrustStore(final Certificate certificate)
      throws IOException, GeneralSecurityException {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null);
    keyStore.setCertificateEntry(alias, certificate);
    return saveStore(keyStore);
  }

  private static Path saveStore(final KeyStore keyStore)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final Path pfxPath = Files.createTempFile(alias, ".pfx");
    pfxPath.toFile().deleteOnExit();
    try (final FileOutputStream outputStream = new FileOutputStream(pfxPath.toFile())) {
      keyStore.store(outputStream, password);
    }
    return pfxPath;
  }
}
