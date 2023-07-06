/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.pki;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Properties;

public class PKCS11Utils {

  public static Path initNSSConfigFile(final Path srcFilePath) {
    // load nss file as Properties
    final Properties nssProp = new Properties();
    try (InputStream input = new FileInputStream(srcFilePath.toFile())) {
      nssProp.load(input);
      String nssDbPath = srcFilePath.getParent().resolve("nssdb").toAbsolutePath().toString();
      nssProp.setProperty("nssSecmodDirectory", nssDbPath);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    // store modified config into temporary file
    final Path targetFilePath = createTemporaryFile("nsscfg");
    try (FileOutputStream outputStream = new FileOutputStream(targetFilePath.toFile())) {
      nssProp.store(outputStream, null);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return targetFilePath;
  }

  private static Path createTemporaryFile(final String suffix) {
    final File tempFile;
    try {
      tempFile = File.createTempFile("temp", suffix);
      tempFile.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Error creating temporary file", e);
    }
    return tempFile.toPath();
  }
}
