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

/** The Enclave key configuration. */
public class EnclaveKeyConfiguration {
  private final String[] pubKeyPaths;
  private final String[] privKeyPaths;
  private EnclaveEncryptorType enclaveEncryptorType;

  /**
   * Instantiates a new Enclave key configuration.
   *
   * @param pubKeyPath the pub key path
   * @param privKeyPath the priv key path
   */
  public EnclaveKeyConfiguration(final String pubKeyPath, final String privKeyPath) {
    this.pubKeyPaths = new String[] {pubKeyPath};
    this.privKeyPaths = new String[] {privKeyPath};
  }

  /**
   * Instantiates a new Enclave key configuration.
   *
   * @param pubKeyPaths the pub key paths
   * @param privKeyPaths the priv key paths
   * @param enclaveEncryptorType the enclave encryptor type
   */
  public EnclaveKeyConfiguration(
      final String[] pubKeyPaths,
      final String[] privKeyPaths,
      final EnclaveEncryptorType enclaveEncryptorType) {
    this.pubKeyPaths = pubKeyPaths;
    this.privKeyPaths = privKeyPaths;
    this.enclaveEncryptorType = enclaveEncryptorType;
  }

  /**
   * Get pub key paths.
   *
   * @return the string [ ]
   */
  public String[] getPubKeyPaths() {
    return pubKeyPaths;
  }

  /**
   * Get private key paths.
   *
   * @return the string [ ]
   */
  public String[] getPrivKeyPaths() {
    return privKeyPaths;
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
