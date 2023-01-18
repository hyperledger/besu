/*
 * Copyright Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.consensus.qbft.pki;

import org.hyperledger.besu.pki.keystore.HardwareKeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.nio.file.Path;

import com.google.common.annotations.VisibleForTesting;

/** The Default key store wrapper provider. */
public class DefaultKeyStoreWrapperProvider implements KeyStoreWrapperProvider {

  private final HardwareKeyStoreWrapperProvider hardwareKeyStoreWrapperProvider;
  private final SoftwareKeyStoreWrapperProvider softwareKeyStoreWrapperProvider;

  /** Instantiates a new Default key store wrapper provider. */
  DefaultKeyStoreWrapperProvider() {
    this(HardwareKeyStoreWrapper::new, SoftwareKeyStoreWrapper::new);
  }

  /**
   * Instantiates a new Default key store wrapper provider.
   *
   * @param hardwareKeyStoreWrapperProvider the hardware key store wrapper provider
   * @param softwareKeyStoreWrapperProvider the software key store wrapper provider
   */
  @VisibleForTesting
  DefaultKeyStoreWrapperProvider(
      final HardwareKeyStoreWrapperProvider hardwareKeyStoreWrapperProvider,
      final SoftwareKeyStoreWrapperProvider softwareKeyStoreWrapperProvider) {
    this.hardwareKeyStoreWrapperProvider = hardwareKeyStoreWrapperProvider;
    this.softwareKeyStoreWrapperProvider = softwareKeyStoreWrapperProvider;
  }

  @Override
  public KeyStoreWrapper apply(
      final String keyStoreType,
      final Path keyStorePath,
      final String keyStorePassword,
      final Path crl) {
    if (KeyStoreWrapper.KEYSTORE_TYPE_PKCS11.equalsIgnoreCase(keyStoreType)) {
      return hardwareKeyStoreWrapperProvider.get(keyStorePassword, keyStorePath, crl);
    } else {
      return softwareKeyStoreWrapperProvider.get(keyStoreType, keyStorePath, keyStorePassword, crl);
    }
  }

  /** The interface Hardware key store wrapper provider. */
  interface HardwareKeyStoreWrapperProvider {

    /**
     * Get hardware key store wrapper.
     *
     * @param keystorePassword the keystore password
     * @param config the config
     * @param crlLocation the crl location
     * @return the hardware key store wrapper
     */
    HardwareKeyStoreWrapper get(
        final String keystorePassword, final Path config, final Path crlLocation);
  }

  /** The interface Software key store wrapper provider. */
  interface SoftwareKeyStoreWrapperProvider {

    /**
     * Get software key store wrapper.
     *
     * @param keystoreType the keystore type
     * @param keystoreLocation the keystore location
     * @param keystorePassword the keystore password
     * @param crlLocation the crl location
     * @return the software key store wrapper
     */
    SoftwareKeyStoreWrapper get(
        final String keystoreType,
        final Path keystoreLocation,
        final String keystorePassword,
        final Path crlLocation);
  }
}
