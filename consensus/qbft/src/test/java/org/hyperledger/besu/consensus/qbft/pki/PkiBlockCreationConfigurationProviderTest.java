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

package org.hyperledger.besu.consensus.qbft.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PkiBlockCreationConfigurationProviderTest {

  @Mock KeyStoreWrapperProvider keyStoreWrapperProvider;
  @Mock KeyStoreWrapper keyStoreWrapper;
  @Mock KeyStoreWrapper trustStoreWrapper;

  @Test
  public void pkiBlockCreationConfigurationIsLoadedCorrectly() {
    when(keyStoreWrapperProvider.apply(any(), eq(Path.of("/tmp/keystore")), eq("pwd"), isNull()))
        .thenReturn(keyStoreWrapper);
    when(keyStoreWrapperProvider.apply(
            any(), eq(Path.of("/tmp/truststore")), eq("pwd"), eq(Path.of("/tmp/crl"))))
        .thenReturn(trustStoreWrapper);

    final PkiKeyStoreConfiguration pkiKeyStoreConfiguration =
        spy(
            new PkiKeyStoreConfiguration.Builder()
                .withKeyStorePath(Path.of("/tmp/keystore"))
                .withKeyStorePasswordPath(Path.of("/tmp/password"))
                .withTrustStorePath(Path.of("/tmp/truststore"))
                .withTrustStorePasswordPath(Path.of("/tmp/password"))
                .withCertificateAlias("anAlias")
                .withCrlFilePath(Path.of("/tmp/crl"))
                .build());
    doReturn("pwd").when(pkiKeyStoreConfiguration).getKeyStorePassword();
    doReturn("pwd").when(pkiKeyStoreConfiguration).getTrustStorePassword();

    final PkiBlockCreationConfigurationProvider pkiBlockCreationConfigProvider =
        new PkiBlockCreationConfigurationProvider(keyStoreWrapperProvider);

    final PkiBlockCreationConfiguration pkiBlockCreationConfiguration =
        pkiBlockCreationConfigProvider.load(pkiKeyStoreConfiguration);

    assertThat(pkiBlockCreationConfiguration).isNotNull();
    assertThat(pkiBlockCreationConfiguration.getKeyStore()).isNotNull();
    assertThat(pkiBlockCreationConfiguration.getTrustStore()).isNotNull();
    assertThat(pkiBlockCreationConfiguration.getCertificateAlias()).isEqualTo("anAlias");
  }
}
