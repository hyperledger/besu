/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.orion.testutil;

import static net.consensys.cava.io.file.Files.copyResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class OrionTestHarnessFactory {

  public static OrionTestHarness create(final Path tempDir, final OrionKeyConfiguration orionConfig)
      throws IOException {
    return create(
        tempDir,
        orionConfig.getPubKeyPath(),
        orionConfig.getPrivKeyPath(),
        Collections.emptyList());
  }

  public static OrionTestHarness create(
      final Path tempDir,
      final String pubKeyPath,
      final String privKeyPath,
      final List<String> othernodes)
      throws IOException {
    Path key1pub = copyResource(pubKeyPath, tempDir.resolve(pubKeyPath));
    Path key1key = copyResource(privKeyPath, tempDir.resolve(privKeyPath));

    return create(tempDir, key1pub, key1key, othernodes);
  }

  public static OrionTestHarness create(
      final Path tempDir, final Path key1pub, final Path key1key, final List<String> othernodes) {

    return new OrionTestHarness(new OrionConfiguration(key1pub, key1key, tempDir, othernodes));
  }
}
