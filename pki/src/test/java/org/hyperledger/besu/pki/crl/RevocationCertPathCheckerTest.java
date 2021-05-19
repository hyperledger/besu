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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileInputStream;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Collections;

import org.junit.Test;

public class RevocationCertPathCheckerTest {

  @Test
  public void validCertificateCheckSucceeds() throws Exception {
    final X509CRL x509CRL = CRLUtil.loadCRL("src/test/resources/crl/crl_valid.crl");
    final RevocationCertPathChecker revocationCertPathChecker =
        new RevocationCertPathChecker(x509CRL);
    final Certificate validCertificate = loadCertificate("valid_certificate.cer");

    revocationCertPathChecker.check(validCertificate, Collections.emptySet());
  }

  @Test
  public void revokedCertificateCheckFails() throws Exception {
    final X509CRL x509CRL = CRLUtil.loadCRL("src/test/resources/crl/crl_valid.crl");
    final RevocationCertPathChecker revocationCertPathChecker =
        new RevocationCertPathChecker(x509CRL);
    final Certificate revokedCertificate = loadCertificate("revoked_certificate.cer");

    assertThatThrownBy(
            () -> revocationCertPathChecker.check(revokedCertificate, Collections.emptySet()))
        .isInstanceOf(CertPathValidatorException.class)
        .hasMessageContaining("Revoked certificate found in certificate path");
  }

  private Certificate loadCertificate(final String name) {
    try {
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      return certificateFactory.generateCertificate(
          new FileInputStream("src/test/resources/crl/" + name));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
