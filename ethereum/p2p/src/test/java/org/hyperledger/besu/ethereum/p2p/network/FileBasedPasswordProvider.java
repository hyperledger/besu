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
package org.hyperledger.besu.ethereum.p2p.network;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class FileBasedPasswordProvider implements Supplier<String> {
  private final Path passwordFile;

  public FileBasedPasswordProvider(final Path passwordFile) {
    requireNonNull(passwordFile, "Password file path cannot be null");
    this.passwordFile = passwordFile;
  }

  @Override
  public String get() {
    try (final Stream<String> fileStream = Files.lines(passwordFile)) {
      return fileStream
          .findFirst()
          .orElseThrow(
              () ->
                  new RuntimeException(
                      String.format(
                          "Unable to read keystore password from %s", passwordFile.toString())));
    } catch (final IOException e) {
      throw new RuntimeException(
          String.format("Unable to read keystore password file %s", passwordFile.toString()), e);
    }
  }
}
