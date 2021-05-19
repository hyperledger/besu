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

import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.X509CRL;
import java.util.Collection;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 This class implements a custom CRL check engine. It takes a single BouncyCastle X509CRL that can
 hold multiple CRL rules for multiple issuers.
*/
public class RevocationCertPathChecker extends PKIXCertPathChecker {

  private static final Logger LOGGER = LogManager.getLogger();

  private final X509CRL crl;

  public RevocationCertPathChecker(final X509CRL crl) {
    this.crl = crl;
  }

  @Override
  public void init(final boolean forward) throws CertPathValidatorException {
    // DO NOTHING
  }

  @Override
  public boolean isForwardCheckingSupported() {
    return true;
  }

  @Override
  public Set<String> getSupportedExtensions() {
    return null;
  }

  @Override
  public void check(final Certificate cert, final Collection<String> unresolvedCritExts)
      throws CertPathValidatorException {
    LOGGER.trace("Checking certificate revocation");

    if (crl.isRevoked(cert)) {
      throw new CertPathValidatorException("Revoked certificate found in certificate path");
    }
  }
}
