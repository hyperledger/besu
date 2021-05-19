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
package org.hyperledger.besu.pki.crl;

import org.hyperledger.besu.pki.PkiException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;

import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CRLUtil {

  public static X509CRL loadCRL(final String path) {
    try {
      return (X509CRL)
          CertificateFactory.getInstance("X509")
              .generateCRL(new FileInputStream(Paths.get(path).toFile()));
    } catch (Exception e) {
      throw new PkiException("Error loading CRL file " + path, e);
    }
  }

  /*
   This method was used to create a CRL file that can be used on the tests
  */
  @VisibleForTesting
  @SuppressWarnings("JdkObsolete")
  public static void createCRLFile(
      final X509Certificate issuer,
      final PrivateKey issuerPrivateKey,
      final Collection<X509Certificate> revokedCertificates,
      final String outputPath)
      throws Exception {
    final ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256withRSA").build(issuerPrivateKey);

    X509v2CRLBuilder x509v2CRLBuilder =
        new X509v2CRLBuilder(
            new JcaX509CertificateHolder(issuer).getIssuer(), Date.from(Instant.now()));

    revokedCertificates.forEach(
        c ->
            x509v2CRLBuilder.addCRLEntry(
                c.getSerialNumber(), Date.from(Instant.now()), CRLReason.privilegeWithdrawn));

    X509CRLHolder x509v2CRLHolder = x509v2CRLBuilder.build(contentSigner);

    FileOutputStream fos = new FileOutputStream(outputPath);
    fos.write(x509v2CRLHolder.getEncoded());
    fos.close();
  }
}
