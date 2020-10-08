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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.account;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

public class PrivacyAccount {

  private final URL privateKeyPath;
  private final URL[] enclaveKeyPaths;
  private final URL[] enclavePrivateKeyPaths;

  private PrivacyAccount(
      final URL privateKeyPath,
      final URL[] enclavePublicKeyPaths,
      final URL[] enclavePrivateKeyPaths) {
    this.privateKeyPath = privateKeyPath;
    this.enclaveKeyPaths = enclavePublicKeyPaths;
    this.enclavePrivateKeyPaths = enclavePrivateKeyPaths;
  }

  public static PrivacyAccount create(
      final URL privateKeyPath, final URL enclavePublicKeyPath, final URL enclavePrivateKeyPath) {
    return new PrivacyAccount(
        privateKeyPath, new URL[] {enclavePublicKeyPath}, new URL[] {enclavePrivateKeyPath});
  }

  public static PrivacyAccount create(
      final URL privateKeyPath,
      final URL[] enclavePublicKeyPath,
      final URL[] enclavePrivateKeyPath) {
    return new PrivacyAccount(privateKeyPath, enclavePublicKeyPath, enclavePrivateKeyPath);
  }

  public String getPrivateKeyPath() {
    return toStringResource(privateKeyPath);
  }

  public String[] getEnclaveKeyPaths() {
    return Arrays.stream(enclaveKeyPaths)
        .map(path -> toStringResource(path))
        .toArray(String[]::new);
  }

  public String[] getEnclavePrivateKeyPaths() {
    return Arrays.stream(enclavePrivateKeyPaths)
        .map(path -> toStringResource(path))
        .toArray(String[]::new);
  }

  private String toStringResource(final URL path) {
    return path.getPath().substring(path.getPath().lastIndexOf(File.separator) + 1);
  }
}
