/*
 * Copyright ConsenSys AG.
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.consensus.qbft.pki.DefaultKeyStoreWrapperProvider.HardwareKeyStoreWrapperProvider;
import org.hyperledger.besu.consensus.qbft.pki.DefaultKeyStoreWrapperProvider.SoftwareKeyStoreWrapperProvider;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DefaultKeyStoreWrapperProviderTest {

  @Mock private HardwareKeyStoreWrapperProvider hardwareKeyStoreWrapperProvider;
  @Mock private SoftwareKeyStoreWrapperProvider softwareKeyStoreWrapperProvider;
  @InjectMocks private DefaultKeyStoreWrapperProvider keyStoreWrapperProvider;

  private final Path keystorePath = Path.of("/keystore");
  private final String keystorePassword = "pwd";
  private final Path crlPath = Path.of("/crl");

  @Test
  public void configWithTypePKCS11UsesHardwareKeyStoreProvider() {
    keyStoreWrapperProvider.apply(
        KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, keystorePath, keystorePassword, crlPath);

    verify(hardwareKeyStoreWrapperProvider)
        .get(eq(keystorePassword), eq(keystorePath), eq(crlPath));
    verifyNoInteractions(softwareKeyStoreWrapperProvider);
  }

  @Test
  public void configWithTypePKCS12UsesSoftwareKeyStoreProvider() {
    keyStoreWrapperProvider.apply(
        KeyStoreWrapper.KEYSTORE_TYPE_PKCS12, keystorePath, keystorePassword, crlPath);

    verify(softwareKeyStoreWrapperProvider)
        .get(
            eq(KeyStoreWrapper.KEYSTORE_TYPE_PKCS12),
            eq(keystorePath),
            eq(keystorePassword),
            eq(crlPath));
    verifyNoInteractions(hardwareKeyStoreWrapperProvider);
  }

  @Test
  public void configWithTypeJKSUsesSoftwareKeyStoreProvider() {
    keyStoreWrapperProvider.apply(
        KeyStoreWrapper.KEYSTORE_TYPE_JKS, keystorePath, keystorePassword, crlPath);

    verify(softwareKeyStoreWrapperProvider)
        .get(
            eq(KeyStoreWrapper.KEYSTORE_TYPE_JKS),
            eq(keystorePath),
            eq(keystorePassword),
            eq(crlPath));
    verifyNoInteractions(hardwareKeyStoreWrapperProvider);
  }
}
