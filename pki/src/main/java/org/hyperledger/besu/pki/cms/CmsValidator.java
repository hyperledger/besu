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

import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.PKIXRevocationChecker.Option;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmsValidator {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(CmsValidator.class);

  private final KeyStoreWrapper truststore;

  public CmsValidator(final KeyStoreWrapper truststore) {
    this.truststore = truststore;
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
    if (cms == null || cms == Bytes.EMPTY) {
      return false;
    }

    try {
      LOGGER.trace("Validating CMS message");

      final CMSSignedData cmsSignedData =
          new CMSSignedData(new CMSProcessableByteArray(expectedContent.toArray()), cms.toArray());
      final X509Certificate signerCertificate = getSignerCertificate(cmsSignedData);

      // Validate msg signature and content
      if (!isSignatureValid(signerCertificate, cmsSignedData)) {
        return false;
      }

      // Validate certificate trust
      if (!isCertificateTrusted(signerCertificate, cmsSignedData)) {
        return false;
      }

      return true;
    } catch (final Exception e) {
      throw new RuntimeException("Error validating CMS data", e);
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
    } catch (final Exception e) {
      throw new RuntimeException("Error retrieving signer certificate from CMS data", e);
    }
  }

  private boolean isSignatureValid(
      final X509Certificate signerCertificate, final CMSSignedData cmsSignedData) {
    LOGGER.trace("Validating CMS signature");
    try {
      return cmsSignedData.verifySignatures(
          sid -> new JcaSimpleSignerInfoVerifierBuilder().build(signerCertificate));
    } catch (final CMSException e) {
      return false;
    }
  }

  private boolean isCertificateTrusted(
      final X509Certificate signerCertificate, final CMSSignedData cmsSignedData) {
    LOGGER.trace("Starting CMS certificate validation");

    try {
      final CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");

      // Define CMS signer certificate as the starting point of the path (leaf certificate)
      final X509CertSelector targetConstraints = new X509CertSelector();
      targetConstraints.setCertificate(signerCertificate);

      // Set parameters for the certificate path building algorithm
      final PKIXBuilderParameters params =
          new PKIXBuilderParameters(truststore.getKeyStore(), targetConstraints);

      // Adding CertStore with CRLs (if present, otherwise disabling revocation check)
      createCRLCertStore(truststore)
          .ifPresentOrElse(
              CRLs -> {
                params.addCertStore(CRLs);
                PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
                rc.setOptions(EnumSet.of(Option.PREFER_CRLS));
                params.addCertPathChecker(rc);
              },
              () -> {
                LOGGER.warn("No CRL CertStore provided. CRL validation will be disabled.");
                params.setRevocationEnabled(false);
              });

      // Read certificates sent on the CMS message and adding it to the path building algorithm
      final CertStore cmsCertificates =
          new JcaCertStoreBuilder().addCertificates(cmsSignedData.getCertificates()).build();
      params.addCertStore(cmsCertificates);

      // Validate certificate path
      try {
        cpb.build(params);
        return true;
      } catch (final CertPathBuilderException cpbe) {
        LOGGER.warn("Untrusted certificate chain", cpbe);
        LOGGER.trace("Reason for failed validation", cpbe.getCause());
        return false;
      }

    } catch (final Exception e) {
      LOGGER.error("Error validating certificate chain");
      throw new RuntimeException("Error validating certificate chain", e);
    }
  }

  private Optional<CertStore> createCRLCertStore(final KeyStoreWrapper truststore) {
    if (truststore.getCRLs() != null) {
      try {
        return Optional.of(
            CertStore.getInstance(
                "Collection", new CollectionCertStoreParameters(truststore.getCRLs())));
      } catch (final Exception e) {
        throw new RuntimeException("Error loading CRLs from Truststore", e);
      }
    } else {
      return Optional.empty();
    }
  }
}
