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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.pki.PkiException;

import java.security.cert.X509CRL;

import org.junit.Test;

public class CRLUtilTest {

  @Test
  public void loadValidCRLFile() {
    // This file has a single revoked certificate
    X509CRL x509CRL = CRLUtil.loadCRL("src/test/resources/crl/crl_valid.crl");

    assertThat(x509CRL.getRevokedCertificates().size()).isEqualTo(1);
  }

  @Test
  public void loadInvalidCRLFile() {
    assertThatThrownBy(() -> CRLUtil.loadCRL("src/test/resources/crl/crl_invalid.crl"))
        .isInstanceOf(PkiException.class)
        .hasMessageContaining("Error loading CRL file");
  }
}
