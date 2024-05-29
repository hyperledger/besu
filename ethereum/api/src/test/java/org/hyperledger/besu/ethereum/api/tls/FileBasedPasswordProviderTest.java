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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileBasedPasswordProviderTest {

  @TempDir private Path folder;

  @Test
  public void passwordCanBeReadFromFile() throws IOException {
    final Path passwordFile = folder.resolve("pass1");
    Files.write(passwordFile, List.of("line1", "line2"));

    final String password = new FileBasedPasswordProvider(passwordFile).get();
    Assertions.assertThat(password).isEqualTo("line1");
  }

  @Test
  public void exceptionRaisedFromReadingEmptyFile() throws IOException {
    final Path passwordFile = folder.resolve("pass2");
    Files.write(passwordFile, new byte[0]);

    Assertions.assertThatExceptionOfType(TlsConfigurationException.class)
        .isThrownBy(
            () -> {
              new FileBasedPasswordProvider(passwordFile).get();
            })
        .withMessageContaining("Unable to read keystore password from");
  }

  @Test
  public void exceptionRaisedFromReadingNonExistingFile() throws IOException {
    Assertions.assertThatExceptionOfType(TlsConfigurationException.class)
        .isThrownBy(
            () -> {
              new FileBasedPasswordProvider(Path.of("/tmp/invalid_file.txt")).get();
            })
        .withMessageContaining("Unable to read keystore password file");
  }
}
