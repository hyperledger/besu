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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.account;

import java.io.File;
import java.net.URL;

public class PrivacyAccount {

  private final URL privateKeyPath;
  private final URL enclaveKeyPath;
  private final URL enclavePrivateKeyPath;

  private PrivacyAccount(
      final URL privateKeyPath, final URL enclavePublicKeyPath, final URL enclavePrivateKeyPath) {
    this.privateKeyPath = privateKeyPath;
    this.enclaveKeyPath = enclavePublicKeyPath;
    this.enclavePrivateKeyPath = enclavePrivateKeyPath;
  }

  public static PrivacyAccount create(
      final URL privateKeyPath, final URL enclavePublicKeyPath, final URL enclavePrivateKeyPath) {
    return new PrivacyAccount(privateKeyPath, enclavePublicKeyPath, enclavePrivateKeyPath);
  }

  public String getPrivateKeyPath() {
    return toStringResource(privateKeyPath);
  }

  public String getEnclaveKeyPath() {
    return toStringResource(enclaveKeyPath);
  }

  public String getEnclavePrivateKeyPath() {
    return toStringResource(enclavePrivateKeyPath);
  }

  private String toStringResource(final URL path) {
    return path.getPath().substring(path.getPath().lastIndexOf(File.separator) + 1);
  }
}
