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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OrionConfiguration {

  private final Path publicKey;
  private final Path privateKey;
  private final Path tempDir;
  private final List<String> otherNodes = new ArrayList<>();

  public OrionConfiguration(
      final Path publicKey,
      final Path privateKey,
      final Path tempDir,
      final List<String> otherNodes) {

    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.tempDir = tempDir;
    this.otherNodes.addAll(otherNodes);
  }

  public Path getPublicKey() {
    return publicKey;
  }

  public Path getPrivateKey() {
    return privateKey;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public List<String> getOtherNodes() {
    return otherNodes;
  }

  public void addOtherNode(final String otherNode) {
    otherNodes.add(otherNode);
  }
}
