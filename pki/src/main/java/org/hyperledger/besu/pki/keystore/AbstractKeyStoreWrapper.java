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
package org.hyperledger.besu.pki.keystore;

import org.hyperledger.besu.pki.PkiException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Collection;
import java.util.stream.Collectors;

public abstract class AbstractKeyStoreWrapper implements KeyStoreWrapper {

  private static final String X_509 = "X.509";

  private final Collection<X509CRL> crls;

  protected AbstractKeyStoreWrapper(final Path crlLocation) {
    super();
    if (null == crlLocation) {
      this.crls = null;
    } else {
      try (InputStream stream = new FileInputStream(crlLocation.toFile())) {
        this.crls =
            CertificateFactory.getInstance(X_509).generateCRLs(stream).stream()
                .map(X509CRL.class::cast)
                .collect(Collectors.toList());
      } catch (final Exception e) {
        throw new PkiException("Failed to initialize software truststore", e);
      }
    }
  }

  @Override
  public Collection<X509CRL> getCRLs() {
    return crls;
  }
}
