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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

public class CmsValidator {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private static final Logger LOGGER = LogManager.getLogger();

  private final KeyStoreWrapper truststore;
  private final Optional<CertStore> crlCertStore;

  public CmsValidator(final KeyStoreWrapper truststore) {
    this(truststore, null);
  }

  public CmsValidator(final KeyStoreWrapper truststore, final CertStore crlCertStore) {
    this.truststore = truststore;
    this.crlCertStore = Optional.ofNullable(crlCertStore);
  }

  /**
   * Verifies that a CMS message signed content matched the expected content, and that the message
   * signer is from a certificate that is trusted (has permission to propose a block)
   *
   * @param cms the CMS message bytes
   * @param expectedContent the expected signed content in the CMS message
   * @return true, if the signed content matched the expected content and the signer's certificate
   *     is trusted, otherwise returns false.
   */
  public boolean validate(final Bytes cms, final Bytes expectedContent) {
    try {
      LOGGER.trace("Decoding CMS message");
      final CMSSignedData cmsSignedData = new CMSSignedData(cms.toArray());

      // Content validation - expected matching block hash
      if (!isSignedDataMatchingExpectedContent(expectedContent, cmsSignedData)) {
        return false;
      }

      // Certificate validation - must be valid and have trusted path
      if (!isCertificateValid(cmsSignedData)) {
        return false;
      }

      return true;
    } catch (Exception e) {
      LOGGER.error("Error validating CMS data", e);
      throw new RuntimeException("Error validating CMS data", e);
    }
  }

  private boolean isSignedDataMatchingExpectedContent(
      final Bytes expectedContent, final CMSSignedData cmsSignedData) throws IOException {
    LOGGER.trace("Starting CMS content validation: expected = {}", expectedContent.toHexString());

    // We expect the data to be of type CMSProcessableByteArray
    final CMSProcessableByteArray signedContent =
        (CMSProcessableByteArray) cmsSignedData.getSignedContent();
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    signedContent.getInputStream().transferTo(baos);
    final Bytes recoveredContent = Bytes.wrap(baos.toByteArray());

    boolean matches = recoveredContent.equals(expectedContent);

    LOGGER.trace(
        "CMS content validation {}: expected = {}, received: {}",
        matches ? "succeeded" : "failed",
        expectedContent.toHexString(),
        recoveredContent.toHexString());

    return matches;
  }

  private boolean isCertificateValid(final CMSSignedData cmsSignedData) {
    LOGGER.trace("Starting CMS certificate validation");

    try {
      final CertStore cmsCertificates;
      try {
        cmsCertificates =
            new JcaCertStoreBuilder().addCertificates(cmsSignedData.getCertificates()).build();
      } catch (GeneralSecurityException e) {
        throw new RuntimeException("Error reading certificate from CMS data", e);
      }

      // Initialize PKIXParameters with truststore, setting the trusted anchors
      final PKIXParameters pkixParameters;
      try {
        pkixParameters = new PKIXParameters(truststore.getKeyStore());
      } catch (KeyStoreException e) {
        throw new RuntimeException("Truststore hasn't been initialized", e);
      } catch (InvalidAlgorithmParameterException e) {
        throw new RuntimeException("Truststore is empty", e);
      }

      // Define signer's certificate as the starting point of the path (leaf certificate)
      final X509Certificate signerCertificate = getSignerCertificate(cmsSignedData);
      final X509CertSelector targetConstraints = new X509CertSelector();
      targetConstraints.setCertificate(signerCertificate);

      // Set parameters for the certificate path building algorithm
      final PKIXBuilderParameters params;
      try {
        params = new PKIXBuilderParameters(pkixParameters.getTrustAnchors(), targetConstraints);
        // Adding CertStore with CRLs
        crlCertStore.ifPresent(params::addCertStore);
        // Adding intermediate certificates from CMS
        params.addCertStore(cmsCertificates);
      } catch (InvalidAlgorithmParameterException e) {
        throw new RuntimeException("Empty trust anchors on truststore", e);
      }

      // Validate certificate path
      try {
        CertPathBuilder.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME).build(params);
        return true;
      } catch (CertPathBuilderException cpbe) {
        LOGGER.warn("Untrusted certificate chain", cpbe);
        return false;
      }

    } catch (Exception e) {
      LOGGER.error("Error validating certificate chain");
      throw new RuntimeException("Error validating certificate chain", e);
    }
  }

  @SuppressWarnings("unchecked")
  private X509Certificate getSignerCertificate(final CMSSignedData cmsSignedData) {
    try {
      final Store<X509CertificateHolder> certificateStore = cmsSignedData.getCertificates();

      // We don't expect more than one signer on the CMS data
      if (cmsSignedData.getSignerInfos().size() != 1) {
        throw new RuntimeException("Only one signer is expected on the CMS message");
      }
      final SignerInformation signerInformation =
          cmsSignedData.getSignerInfos().getSigners().stream().findFirst().get();

      // Find signer's certificate from CMS data
      final Collection<X509CertificateHolder> signerCertificates =
          certificateStore.getMatches(signerInformation.getSID());
      final X509CertificateHolder certificateHolder = signerCertificates.stream().findFirst().get();

      return new JcaX509CertificateConverter().getCertificate(certificateHolder);
    } catch (Exception e) {
      LOGGER.error("Error retrieving signer certificate from CMS data", e);
      throw new RuntimeException("Error retrieving signer certificate from CMS data", e);
    }
  }
}
