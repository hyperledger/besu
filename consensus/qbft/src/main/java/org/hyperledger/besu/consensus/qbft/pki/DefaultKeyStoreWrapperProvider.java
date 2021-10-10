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

public class DefaultKeyStoreWrapperProvider implements KeyStoreWrapperProvider {

  private final HardwareKeyStoreWrapperProvider hardwareKeyStoreWrapperProvider;
  private final SoftwareKeyStoreWrapperProvider softwareKeyStoreWrapperProvider;

  DefaultKeyStoreWrapperProvider() {
    this(HardwareKeyStoreWrapper::new, SoftwareKeyStoreWrapper::new);
  }

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

  interface HardwareKeyStoreWrapperProvider {

    HardwareKeyStoreWrapper get(
        final String keystorePassword, final Path config, final Path crlLocation);
  }

  interface SoftwareKeyStoreWrapperProvider {

    SoftwareKeyStoreWrapper get(
        final String keystoreType,
        final Path keystoreLocation,
        final String keystorePassword,
        final Path crlLocation);
  }
}
