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

public class EnclaveKeyConfiguration {
  private final String[] pubKeyPaths;
  private final String[] privKeyPaths;

  public EnclaveKeyConfiguration(final String pubKeyPath, final String privKeyPath) {
    this.pubKeyPaths = new String[] {pubKeyPath};
    this.privKeyPaths = new String[] {privKeyPath};
  }

  public EnclaveKeyConfiguration(final String[] pubKeyPaths, final String[] privKeyPaths) {
    this.pubKeyPaths = pubKeyPaths;
    this.privKeyPaths = privKeyPaths;
  }

  public String[] getPubKeyPaths() {
    return pubKeyPaths;
  }

  public String[] getPrivKeyPaths() {
    return privKeyPaths;
  }
}
