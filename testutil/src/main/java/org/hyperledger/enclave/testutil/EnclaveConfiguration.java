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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Enclave configuration. */
public class EnclaveConfiguration {

  private final Path[] publicKeys;
  private final Path[] privateKeys;
  private final EnclaveEncryptorType enclaveEncryptorType;
  private final Path tempDir;
  private final List<String> otherNodes = new ArrayList<>();
  private final boolean clearKnownNodes;
  private final String storage;
  private final String name;

  /**
   * Instantiates a new Enclave configuration.
   *
   * @param name the name
   * @param publicKeys the public keys
   * @param privateKeys the private keys
   * @param enclaveEncryptorType the enclave encryptor type
   * @param tempDir the temp dir
   * @param otherNodes the other nodes
   * @param clearKnownNodes the clear known nodes
   * @param storage the storage
   */
  public EnclaveConfiguration(
      final String name,
      final Path[] publicKeys,
      final Path[] privateKeys,
      final EnclaveEncryptorType enclaveEncryptorType,
      final Path tempDir,
      final List<String> otherNodes,
      final boolean clearKnownNodes,
      final String storage) {

    this.publicKeys = publicKeys;
    this.privateKeys = privateKeys;
    this.enclaveEncryptorType = enclaveEncryptorType;
    this.tempDir = tempDir;
    this.otherNodes.addAll(otherNodes);
    this.clearKnownNodes = clearKnownNodes;
    this.storage = storage;
    this.name = name;
  }

  /**
   * Get public keys.
   *
   * @return the path [ ]
   */
  public Path[] getPublicKeys() {
    return publicKeys;
  }

  /**
   * Get private keys.
   *
   * @return the path [ ]
   */
  public Path[] getPrivateKeys() {
    return privateKeys;
  }

  /**
   * Gets temp dir.
   *
   * @return the temp dir
   */
  public Path getTempDir() {
    return tempDir;
  }

  /**
   * Gets other nodes.
   *
   * @return the other nodes
   */
  public List<String> getOtherNodes() {
    return otherNodes;
  }

  /**
   * Add other node.
   *
   * @param otherNode the other node
   */
  public void addOtherNode(final String otherNode) {
    otherNodes.add(otherNode);
  }

  /**
   * Is clear known nodes boolean.
   *
   * @return the boolean
   */
  public boolean isClearKnownNodes() {
    return clearKnownNodes;
  }

  /**
   * Gets storage.
   *
   * @return the storage
   */
  public String getStorage() {
    return storage;
  }

  /**
   * Gets name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets enclave encryptor type.
   *
   * @return the enclave encryptor type
   */
  public EnclaveEncryptorType getEnclaveEncryptorType() {
    return enclaveEncryptorType;
  }
}
