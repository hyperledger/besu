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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized;

public class SoftwareKeyStoreFileWrapperTest extends BaseKeyStoreFileWrapperTest {

  private static final String p12KeyStore = "/keystore/partner1client1/keys.p12";
  private static final String jksKeyStore = "/keystore/partner1client1/keystore.jks";
  private static final String trustStore = "/keystore/partner1client1/truststore.jks";
  private static final String crl = "/keystore/partner1client1/crl.pem";
  private static final String validKeystorePassword = "test123";

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "SoftwareKeyStoreWrapper[PKCS12 keystore only]",
            false,
            getPKCS12SoftwareKeyStoreWrapper()
          },
          {
            "SoftwareKeyStoreWrapper[JKS keystore only]",
            false,
            getJKSSoftwareKeyStoreWrapper(false)
          },
          {
            "SoftwareKeyStoreWrapper[JKS keystore/truststore]",
            true,
            getJKSSoftwareKeyStoreWrapper(true)
          }
        });
  }

  private static KeyStoreWrapper getPKCS12SoftwareKeyStoreWrapper() {
    try {
      return new SoftwareKeyStoreWrapper(
          KeyStoreWrapper.KEYSTORE_TYPE_PKCS12,
          toPath(p12KeyStore),
          validKeystorePassword,
          toPath(crl));
    } catch (final Exception e) {
      throw new PkiException("Failed to initialize software keystore", e);
    }
  }

  private static KeyStoreWrapper getJKSSoftwareKeyStoreWrapper(final boolean setupTruststore) {
    try {
      final Path keystoreLocation = toPath(jksKeyStore);
      if (setupTruststore) {
        final Path truststoreLocation = toPath(trustStore);
        // password shouldn't be needed for retrieving certificate from truststore
        return new SoftwareKeyStoreWrapper(
            KeyStoreWrapper.KEYSTORE_TYPE_JKS,
            keystoreLocation,
            validKeystorePassword,
            KeyStoreWrapper.KEYSTORE_TYPE_JKS,
            truststoreLocation,
            null,
            toPath(crl));
      }
      return new SoftwareKeyStoreWrapper(
          KeyStoreWrapper.KEYSTORE_TYPE_JKS, keystoreLocation, validKeystorePassword, toPath(crl));
    } catch (final Exception e) {
      throw new PkiException("Failed to initialize software keystore", e);
    }
  }
}
