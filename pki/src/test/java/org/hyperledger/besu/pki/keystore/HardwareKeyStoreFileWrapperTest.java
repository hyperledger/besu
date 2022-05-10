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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.pki.PkiException;

import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.runners.Parameterized;

public class HardwareKeyStoreFileWrapperTest extends BaseKeyStoreFileWrapperTest {

  private static final String config = "/keystore/partner1client1/nss.cfg";
  private static final String crl = "/keystore/partner1client1/crl.pem";
  private static final String configName = "NSScrypto-partner1client1";
  private static final String validKeystorePassword = "test123";

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "HardwareKeyStoreWrapper[PKCS11 keystore/truststore]",
            true,
            CryptoTestUtil.isNSSLibInstalled() ? getHardwareKeyStoreWrapper(configName) : null
          }
        });
  }

  private static KeyStoreWrapper getHardwareKeyStoreWrapper(final String cfgName) {
    try {
      final Path path = toPath(config);
      final Path crlPath = toPath(crl);
      final Optional<Provider> existingProvider =
          Stream.of(Security.getProviders())
              .filter(p -> p.getName().equals("SunPKCS11" + cfgName))
              .findAny();
      return existingProvider
          .map(provider -> new HardwareKeyStoreWrapper(validKeystorePassword, provider, crlPath))
          .orElseGet(() -> new HardwareKeyStoreWrapper(validKeystorePassword, path, crlPath));
    } catch (final Exception e) {
      if (OS.MAC.isCurrentOs()) {
        // nss3 is difficult to setup on mac correctly, don't let it break unit tests for dev
        // machines.
        Assume.assumeNoException("Failed to initialize hardware keystore", e);
      }
      // Not a mac, probably a production build. Full failure.
      throw new PkiException("Failed to initialize hardware keystore", e);
    }
  }

  @Before
  public void beforeMethod() {
    Assume.assumeTrue(
        "Test ignored due to NSS library not being installed/detected.",
        CryptoTestUtil.isNSSLibInstalled());
  }

  @Test
  public void getPkcs11Provider() throws Exception {
    final HardwareKeyStoreWrapper sut =
        (HardwareKeyStoreWrapper) getHardwareKeyStoreWrapper(configName);
    assertThatThrownBy(() -> sut.getPkcs11ProviderForConfig("no-library"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void init_keystorePassword_config() throws Exception {
    new HardwareKeyStoreWrapper(validKeystorePassword, toPath(config), toPath(crl));
  }

  @Test
  public void init_keystorePassword_config_invalid() throws Exception {
    final String config = "invalid";
    assertThatThrownBy(
            () -> new HardwareKeyStoreWrapper(validKeystorePassword, toPath(config), toPath(crl)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void init_keystorePassword_config_missing_pw() throws Exception {
    assertThatThrownBy(() -> new HardwareKeyStoreWrapper(null, toPath(config), toPath(crl)))
        .isInstanceOf(PkiException.class);
  }

  @Test
  public void init_keystorePassword_provider_missing_pw() throws Exception {
    final Provider p = null;
    assertThatThrownBy(() -> new HardwareKeyStoreWrapper(validKeystorePassword, p, toPath(crl)))
        .isInstanceOf(PkiException.class);
  }
}
