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
package org.hyperledger.enclave.testutil;

import static org.apache.tuweni.io.file.Files.copyResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OrionTestHarnessFactory {
  private static final String storage = "memory";

  public static OrionTestHarness create(
      final String name, final Path tempDir, final EnclaveKeyConfiguration orionConfig) {
    return create(
        name,
        tempDir,
        orionConfig.getPubKeyPaths(),
        orionConfig.getPrivKeyPaths(),
        Collections.emptyList());
  }

  public static OrionTestHarness create(
      final String name,
      final Path tempDir,
      final String[] pubKeyPaths,
      final String[] privKeyPaths,
      final List<String> othernodes) {
    final Path[] pubKeys =
        Arrays.stream(pubKeyPaths)
            .map(
                (pk) -> {
                  try {
                    return copyResource(pk, tempDir.resolve(pk));
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(Path[]::new);
    final Path[] privKeys =
        Arrays.stream(privKeyPaths)
            .map(
                (pk) -> {
                  try {
                    return copyResource(pk, tempDir.resolve(pk));
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(Path[]::new);

    return create(name, tempDir, pubKeys, privKeys, othernodes);
  }

  public static OrionTestHarness create(
      final String name,
      final Path tempDir,
      final Path[] key1pubs,
      final Path[] key1keys,
      final List<String> othernodes) {
    return new OrionTestHarness(
        new EnclaveConfiguration(name, key1pubs, key1keys, tempDir, othernodes, false, storage));
  }
}
