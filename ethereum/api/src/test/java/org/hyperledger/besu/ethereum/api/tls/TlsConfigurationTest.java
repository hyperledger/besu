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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TlsConfigurationTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void validTrustOptionsFileWorks() throws IOException {
    final Path tempFile = temporaryFolder.newFile().toPath();
    Files.write(
        tempFile,
        List.of(
            "localhost DF:65:B8:02:08:5E:91:82:0F:91:F5:1C:96:56:92:C4:1A:F6:C6:27:FD:6C:FC:31:F2:BB:90:17:22:59:5B:50"),
        UTF_8);
    final TlsConfiguration tlsConfiguration =
        TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
            .withKeyStorePath(temporaryFolder.newFolder().toPath())
            .withKeyStorePassword("test")
            .withKnownClientsFile(tempFile)
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
            () ->
                TlsConfiguration.TlsConfigurationBuilder.aTlsConfiguration()
                    .withKeyStorePath(temporaryFolder.newFolder().toPath())
                    .withKeyStorePassword("test")
                    .withKnownClientsFile(tempFile)
                    .build())
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
}
