/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;

import java.io.File;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PkiOptionsTest extends CommandTestAbstract {

  @Test
  public void pkiBlockCreationIsDisabledByDefault() {
    parseCommand();

    verifyNoInteractions(mockPkiBlockCreationConfigProvider);
  }

  @Test
  public void pkiBlockCreationKeyStoreFileRequired() {
    parseCommand(
        "--Xpki-block-creation-enabled",
        "--Xpki-block-creation-keystore-password-file",
        "/tmp/pwd");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("KeyStore file is required when PKI Block Creation is enabled");
  }

  @Test
  public void pkiBlockCreationPasswordFileRequired() {
    parseCommand(
        "--Xpki-block-creation-enabled", "--Xpki-block-creation-keystore-file", "/tmp/keystore");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "File containing password to unlock keystore is required when PKI Block Creation is enabled");
  }

  @Test
  public void pkiBlockCreationFullConfig(final @TempDir Path pkiTempFolder) throws Exception {
    // Create temp file with password
    final File pwdFile = pkiTempFolder.resolve("pwd").toFile();
    FileUtils.writeStringToFile(pwdFile, "foo", UTF_8);

    parseCommand(
        "--Xpki-block-creation-enabled",
        "--Xpki-block-creation-keystore-type",
        "JKS",
        "--Xpki-block-creation-keystore-file",
        "/tmp/keystore",
        "--Xpki-block-creation-keystore-password-file",
        pwdFile.getAbsolutePath(),
        "--Xpki-block-creation-keystore-certificate-alias",
        "anAlias",
        "--Xpki-block-creation-truststore-type",
        "JKS",
        "--Xpki-block-creation-truststore-file",
        "/tmp/truststore",
        "--Xpki-block-creation-truststore-password-file",
        pwdFile.getAbsolutePath(),
        "--Xpki-block-creation-crl-file",
        "/tmp/crl");

    final PkiKeyStoreConfiguration pkiKeyStoreConfig =
        pkiKeyStoreConfigurationArgumentCaptor.getValue();

    assertThat(pkiKeyStoreConfig).isNotNull();
    assertThat(pkiKeyStoreConfig.getKeyStoreType()).isEqualTo("JKS");
    assertThat(pkiKeyStoreConfig.getKeyStorePath()).isEqualTo(Path.of("/tmp/keystore"));
    assertThat(pkiKeyStoreConfig.getKeyStorePassword()).isEqualTo("foo");
    assertThat(pkiKeyStoreConfig.getCertificateAlias()).isEqualTo("anAlias");
    assertThat(pkiKeyStoreConfig.getTrustStoreType()).isEqualTo("JKS");
    assertThat(pkiKeyStoreConfig.getTrustStorePath()).isEqualTo(Path.of("/tmp/truststore"));
    assertThat(pkiKeyStoreConfig.getTrustStorePassword()).isEqualTo("foo");
    assertThat(pkiKeyStoreConfig.getCrlFilePath()).hasValue(Path.of("/tmp/crl"));
  }
}
