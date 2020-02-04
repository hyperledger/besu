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

package org.hyperledger.besu.enclave;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;

import com.google.common.io.Resources;

public class TlsCertificateDefinition {

  private final File pkcs12File;
  private final String password;

  public static TlsCertificateDefinition loadFromResource(
      final String resourcePath, final String password) {
    try {
      final URL sslCertificate = Resources.getResource(resourcePath);
      final Path keystorePath = Path.of(sslCertificate.getPath());

      return new TlsCertificateDefinition(keystorePath.toFile(), password);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to load TLS certificates", e);
    }
  }

  public TlsCertificateDefinition(final File pkcs12File, final String password) {
    this.pkcs12File = pkcs12File;
    this.password = password;
  }

  public File getPkcs12File() {
    return pkcs12File;
  }

  public String getPassword() {
    return password;
  }
}
