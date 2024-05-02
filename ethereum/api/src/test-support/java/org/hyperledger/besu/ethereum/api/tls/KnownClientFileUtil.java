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

public class KnownClientFileUtil {

  public static void writeToKnownClientsFile(
      final String commonName, final String fingerprint, final Path knownClientsFile) {
    try {
      final String knownClientsLine = String.format("%s %s", commonName, fingerprint);
      Files.write(knownClientsFile, List.of("#Known Clients File", knownClientsLine));
    } catch (final IOException e) {
      throw new RuntimeException("Error in updating known clients file", e);
    }
  }
}
