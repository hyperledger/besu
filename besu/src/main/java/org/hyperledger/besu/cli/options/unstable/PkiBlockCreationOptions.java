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

package org.hyperledger.besu.cli.options.unstable;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;

import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider;
import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;

import java.nio.file.Path;
import java.util.Optional;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

public class PkiBlockCreationOptions {

  @Option(
      names = {"--pki-block-creation-enabled"},
      description = "Enable PKI integration (default: ${DEFAULT-VALUE})")
  Boolean enabled = false;

  @Option(
      names = {"--pki-block-creation-keystore-type"},
      paramLabel = "<NAME>",
      description = "PKI service keystore type. Required if PKI Integration is enabled.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String keyStoreType = PkiKeyStoreConfiguration.DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--pki-block-creation-keystore-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Keystore containing key/certificate for PKI Integration.")
  Path keyStoreFile = null;

  @Option(
      names = {"--pki-block-creation-keystore-password-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "File containing password to unlock keystore for PKI Integration. Required if PKI Integration is enabled.")
  Path keyStorePasswordFile = null;

  @Option(
      names = {"--pki-block-creation-keystore-certificate-alias"},
      paramLabel = "<NAME>",
      description =
          "Alias of the certificate that will be included in the blocks proposed by this validator.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String certificateAlias = PkiKeyStoreConfiguration.DEFAULT_CERTIFICATE_ALIAS;

  @Option(
      names = {"--pki-block-creation-truststore-type"},
      paramLabel = "<NAME>",
      description = "PKI Integration truststore type.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String trustStoreType = PkiKeyStoreConfiguration.DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--pki-block-creation-truststore-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Truststore containing trusted certificates for PKI Integration.")
  Path trustStoreFile = null;

  @Option(
      names = {"--pki-block-creation-truststore-password-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "File containing password to unlock truststore for PKI Integration.")
  Path trustStorePasswordFile = null;

  @Option(
      names = {"--pki-block-creation-crl-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Certificate revocation list for the PKI Integration.")
  Path crlFile = null;

  public Optional<PkiKeyStoreConfiguration> asDomainConfig(final CommandLine commandLine) {
    if (!enabled) {
      return Optional.empty();
    }

    if (keyStoreType == null) {
      throw new ParameterException(
          commandLine, "Keystore type is required when p2p SSL is enabled");
    }

    if (keyStorePasswordFile == null) {
      throw new ParameterException(
          commandLine,
          "File containing password to unlock keystore is required when p2p SSL is enabled");
    }

    return Optional.of(
        new PkiKeyStoreConfiguration.Builder()
            .withKeyStoreType(keyStoreType)
            .withKeyStorePath(keyStoreFile)
            .withKeyStorePasswordSupplier(new FileBasedPasswordProvider(keyStorePasswordFile))
            .withCertificateAlias(certificateAlias)
            .withTrustStoreType(trustStoreType)
            .withTrustStorePath(trustStoreFile)
            .withTrustStorePasswordSupplier(
                null == trustStorePasswordFile
                    ? null
                    : new FileBasedPasswordProvider(trustStorePasswordFile))
            .withCrlFilePath(crlFile)
            .build());
  }
}
