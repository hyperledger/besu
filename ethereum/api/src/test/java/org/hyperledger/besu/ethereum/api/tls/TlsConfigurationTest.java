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
package org.hyperledger.besu.ethereum.api.tls;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TlsConfigurationTest {
  private static final String KNOWN_CLIENTS_RESOURCE = "JsonRpcHttpService/rpc_known_clients.txt";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void validTrustOptionsFileWorks() throws IOException {
    final TlsConfiguration tlsConfiguration =
        TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
            .withKeyStorePath(temporaryFolder.newFolder().toPath())
            .withKeyStorePassword("test")
            .withKnownClientsFile(getKnownClientsFile())
            .build();
    Assertions.assertThat(tlsConfiguration).isNotNull();
    Assertions.assertThat(tlsConfiguration.getTrustOptions().isPresent()).isTrue();
  }

  @Test
  public void invalidTrustOptionsFileFails() throws IOException {
    final Path tempFile = temporaryFolder.newFile().toPath();
    Files.write(tempFile, List.of("common_name invalid_sha256"), UTF_8);
    Assertions.assertThatExceptionOfType(TlsConfigurationException.class)
        .isThrownBy(
            () -> {
              TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
                  .withKeyStorePath(temporaryFolder.newFolder().toPath())
                  .withKeyStorePassword("test")
                  .withKnownClientsFile(tempFile)
                  .build();
            })
        .withMessageContaining("Invalid fingerprint in");
  }

  @Test
  public void emptyTrustOptionsFileWorks() throws IOException {
    final File tempFile = temporaryFolder.newFile();
    final TlsConfiguration tlsConfiguration =
        TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
            .withKeyStorePath(temporaryFolder.newFolder().toPath())
            .withKeyStorePassword("test")
            .withKnownClientsFile(tempFile.toPath())
            .build();
    Assertions.assertThat(tlsConfiguration).isNotNull();
    Assertions.assertThat(tlsConfiguration.getTrustOptions().isPresent()).isTrue();
  }

  @Test
  public void trustOptionsIsEmptyWhenNotSet() throws IOException {
    final TlsConfiguration tlsConfiguration =
        TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
            .withKeyStorePath(temporaryFolder.newFolder().toPath())
            .withKeyStorePassword("test")
            .build();
    Assertions.assertThat(tlsConfiguration).isNotNull();
    Assertions.assertThat(tlsConfiguration.getTrustOptions().isEmpty()).isTrue();
  }

  private Path getKnownClientsFile() {
    return Paths.get(getResource(KNOWN_CLIENTS_RESOURCE).getPath());
  }
}
