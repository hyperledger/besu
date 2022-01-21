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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Charsets;

public class PKCS11Utils {

  public static Path initNSSConfigFile(final Path srcFilePath) {
    Path ret = null;
    try {
      final String content = Files.readString(srcFilePath);
      final String updated =
          content.replaceAll(
              "(nssSecmodDirectory\\W*)(\\.\\/.*)",
              "$1".concat(srcFilePath.toAbsolutePath().toString().replace("nss.cfg", "nssdb")));
      final Path targetFilePath = createTemporaryFile("nsscfg");
      Files.write(targetFilePath, updated.getBytes(Charsets.UTF_8));
      ret = targetFilePath;
    } catch (IOException e) {
      throw new RuntimeException("Error populating nss config file", e);
    }
    return ret;
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
