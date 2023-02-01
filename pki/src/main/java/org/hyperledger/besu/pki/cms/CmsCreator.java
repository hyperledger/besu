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

import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.EllipticCurve;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

/** The Cms creator. */
public class CmsCreator {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private final String certificateAlias;
  private final KeyStoreWrapper keyStore;

  /**
   * Instantiates a new Cms creator.
   *
   * @param keyStore the key store
   * @param certificateAlias the certificate alias
   */
  public CmsCreator(final KeyStoreWrapper keyStore, final String certificateAlias) {
    this.keyStore = keyStore;
    this.certificateAlias = certificateAlias;
  }

  /**
   * Creates a CMS message with the content parameter, signed with the certificate with alias
   * defined in the {@code CmsCreator} constructor. The certificate chain is also included so the
   * recipient has the information needed to build a trusted certificate path when validating this
   * message.
   *
   * @param contentToSign the content that will be signed and added to the message
   * @return the CMS message bytes
   */
  @SuppressWarnings("rawtypes")
  public Bytes create(final Bytes contentToSign) {
    try {
      // Certificates that will be sent
      final List<X509Certificate> x509Certificates =
          Stream.of(keyStore.getCertificateChain(certificateAlias))
              .map(X509Certificate.class::cast)
              .collect(Collectors.toList());
      final Store certs = new JcaCertStore(x509Certificates);

      // Private key of the certificate that will sign the message
      final PrivateKey privateKey = keyStore.getPrivateKey(certificateAlias);

      final ContentSigner contentSigner =
          new JcaContentSignerBuilder(
                  getPreferredSignatureAlgorithm(keyStore.getPublicKey(certificateAlias)))
              .build(privateKey);

      final CMSSignedDataGenerator cmsGenerator = new CMSSignedDataGenerator();

      // Additional intermediate certificates for path building
      cmsGenerator.addCertificates(certs);

      final DigestCalculatorProvider digestCalculatorProvider =
          new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
      // The first certificate in the list (leaf certificate is the signer)
      cmsGenerator.addSignerInfoGenerator(
          new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
              .build(contentSigner, x509Certificates.get(0)));

      // Add signed content
      final CMSTypedData cmsData = new CMSProcessableByteArray(contentToSign.toArray());
      final CMSSignedData cmsSignedData = cmsGenerator.generate(cmsData, false);

      return Bytes.wrap(cmsSignedData.getEncoded());
    } catch (final Exception e) {
      throw new RuntimeException("Error creating CMS data", e);
    }
  }

  /**
   * Gets preferred signature algorithm for EC or RSA keys
   *
   * @param pub the public key
   * @return the preferred signature algorithm
   */
  @VisibleForTesting
  public static String getPreferredSignatureAlgorithm(final PublicKey pub) {
    switch (pub.getAlgorithm()) {
      case "EC" -> {
        final EllipticCurve curve = ((ECPublicKey) pub).getParams().getCurve();
        return switch (curve.getField().getFieldSize()) {
          case 224, 256 -> "SHA256withECDSA";
          case 384 -> "SHA384withECDSA";
          case 521 -> "SHA512withECDSA";
          default -> throw new IllegalArgumentException("Elliptic curve not supported: " + curve);
        };
      }
      case "RSA" -> {
        return "SHA256WithRSAEncryption";
      }
      default -> throw new UnsupportedOperationException(
          "Private key algorithm not supported: " + pub.getAlgorithm());
    }
  }
}
