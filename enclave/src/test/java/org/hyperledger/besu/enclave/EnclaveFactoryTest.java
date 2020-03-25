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
package org.hyperledger.besu.enclave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.util.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EnclaveFactoryTest {
  @ClassRule public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private static final String EMPTY_FILE_ERROR_MSG = "Keystore password file is empty: %s";

  @Test
  public void passwordCanBeReadFromFile() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    Files.writeString(passwordFile.toPath(), "test" + System.lineSeparator() + "test2");
    assertThat(EnclaveFactory.readSecretFromFile(passwordFile.toPath())).isEqualTo("test");
  }

  @Test
  public void emptyFileThrowsException() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(() -> EnclaveFactory.readSecretFromFile(passwordFile.toPath()))
        .withMessage(EMPTY_FILE_ERROR_MSG, passwordFile);
  }

  @Test
  public void fileWithOnlyEoLThrowsException() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    Files.writeString(passwordFile.toPath(), System.lineSeparator());
    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(() -> EnclaveFactory.readSecretFromFile(passwordFile.toPath()))
        .withMessage(EMPTY_FILE_ERROR_MSG, passwordFile);
  }
}
