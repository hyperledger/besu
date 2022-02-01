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

import static java.util.Arrays.asList;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;

import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

public class PkiBlockCreationOptions {

  @Option(
      names = {"--Xpki-block-creation-enabled"},
      hidden = true,
      description = "Enable PKI integration (default: ${DEFAULT-VALUE})")
  Boolean enabled = false;

  @Option(
      names = {"--Xpki-block-creation-keystore-type"},
      hidden = true,
      paramLabel = "<NAME>",
      description = "PKI service keystore type. Required if PKI Block Creation is enabled.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String keyStoreType = PkiKeyStoreConfiguration.DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--Xpki-block-creation-keystore-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Keystore containing key/certificate for PKI Block Creation.")
  Path keyStoreFile = null;

  @Option(
      names = {"--Xpki-block-creation-keystore-password-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "File containing password to unlock keystore for PKI Integration. Required if PKI Block Creation is enabled.")
  Path keyStorePasswordFile = null;

  @Option(
      names = {"--Xpki-block-creation-keystore-certificate-alias"},
      hidden = true,
      paramLabel = "<NAME>",
      description =
          "Alias of the certificate that will be included in the blocks proposed by this validator.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String certificateAlias = PkiKeyStoreConfiguration.DEFAULT_CERTIFICATE_ALIAS;

  @Option(
      names = {"--Xpki-block-creation-truststore-type"},
      hidden = true,
      paramLabel = "<NAME>",
      description = "PKI Integration truststore type.")
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  String trustStoreType = PkiKeyStoreConfiguration.DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--Xpki-block-creation-truststore-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Truststore containing trusted certificates for PKI Block Creation.")
  Path trustStoreFile = null;

  @Option(
      names = {"--Xpki-block-creation-truststore-password-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "File containing password to unlock truststore for PKI Block Creation.")
  Path trustStorePasswordFile = null;

  @Option(
      names = {"--Xpki-block-creation-crl-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "File with all CRLs for PKI Block Creation.")
  Path crlFile = null;

  public Optional<PkiKeyStoreConfiguration> asDomainConfig(final CommandLine commandLine) {
    if (!enabled) {
      return Optional.empty();
    }

    if (keyStoreFile == null) {
      throw new ParameterException(
          commandLine, "KeyStore file is required when PKI Block Creation is enabled");
    }

    if (keyStorePasswordFile == null) {
      throw new ParameterException(
          commandLine,
          "File containing password to unlock keystore is required when PKI Block Creation is enabled");
    }

    return Optional.of(
        new PkiKeyStoreConfiguration.Builder()
            .withKeyStoreType(keyStoreType)
            .withKeyStorePath(keyStoreFile)
            .withKeyStorePasswordPath(keyStorePasswordFile)
            .withCertificateAlias(certificateAlias)
            .withTrustStoreType(trustStoreType)
            .withTrustStorePath(trustStoreFile)
            .withTrustStorePasswordPath(trustStorePasswordFile)
            .withCrlFilePath(crlFile)
            .build());
  }

  public void checkPkiBlockCreationOptionsDependencies(
      final Logger logger, final CommandLine commandLine) {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--Xpki-block-creation-enabled",
        !enabled,
        asList(
            "--Xpki-block-creation-keystore-file", "--Xpki-block-creation-keystore-password-file"));
  }
}
