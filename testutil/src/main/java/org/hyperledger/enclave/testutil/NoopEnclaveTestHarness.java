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

import static com.google.common.io.Files.readLines;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import org.apache.tuweni.io.file.Files;

public class NoopEnclaveTestHarness implements EnclaveTestHarness {

  private final Path tempDir;
  private final EnclaveKeyConfiguration keyConfig;

  public NoopEnclaveTestHarness(final Path tempDir, final EnclaveKeyConfiguration keyConfig) {
    this.tempDir = tempDir;
    this.keyConfig = keyConfig;

    try {
      copyKeys(keyConfig.getPrivKeyPaths());
      copyKeys(keyConfig.getPubKeyPaths());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void copyKeys(final String[] keys) throws IOException {
    for (final String resource : keys) {
      Files.copyResource(resource, tempDir.resolve(resource));
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void close() {}

  @Override
  public List<Path> getPublicKeyPaths() {
    return Arrays.stream(keyConfig.getPubKeyPaths())
        .map(tempDir::resolve)
        .collect(Collectors.toList());
  }

  @Override
  public String getDefaultPublicKey() {
    return getPublicKeys().get(0);
  }

  @Override
  public List<String> getPublicKeys() {
    return Arrays.stream(keyConfig.getPubKeyPaths())
        .map(
            x -> {
              try {
                return readLines(Path.of(tempDir.toString(), x).toFile(), Charsets.UTF_8).get(0);
              } catch (IOException e) {
                return e.getMessage();
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public URI clientUrl() {
    return URI.create("http://noop:8080");
  }

  @Override
  public URI nodeUrl() {
    return URI.create("http://noop:8080");
  }

  @Override
  public void addOtherNode(final URI otherNode) {}

  @Override
  public EnclaveType getEnclaveType() {
    return EnclaveType.NOOP;
  }
}
