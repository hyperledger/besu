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
import java.nio.file.Paths;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509CRL;
import java.util.List;
import java.util.stream.Collectors;

public class CRLUtil {

  public static CertStore loadCRLs(final String path) {
    try {
      final List<X509CRL> crls =
          CertificateFactory.getInstance("X509")
              .generateCRLs(new FileInputStream(Paths.get(path).toFile()))
              .stream()
              .map(X509CRL.class::cast)
              .collect(Collectors.toList());

      return CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls));
    } catch (Exception e) {
      throw new PkiException("Error loading CRL file " + path, e);
    }
  }
}
