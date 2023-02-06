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

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** The interface Enclave test harness. */
public interface EnclaveTestHarness {

  /** Start. */
  void start();

  /** Stop. */
  void stop();

  /** Close. */
  void close();

  /**
   * Gets public key paths.
   *
   * @return the public key paths
   */
  List<Path> getPublicKeyPaths();

  /**
   * Gets default public key.
   *
   * @return the default public key
   */
  String getDefaultPublicKey();

  /**
   * Gets public keys.
   *
   * @return the public keys
   */
  List<String> getPublicKeys();

  /**
   * Client url uri.
   *
   * @return the uri
   */
  URI clientUrl();

  /**
   * Node url uri.
   *
   * @return the uri
   */
  URI nodeUrl();

  /**
   * Add other node.
   *
   * @param otherNode the other node
   */
  void addOtherNode(final URI otherNode);

  /**
   * Gets enclave type.
   *
   * @return the enclave type
   */
  EnclaveType getEnclaveType();
}
