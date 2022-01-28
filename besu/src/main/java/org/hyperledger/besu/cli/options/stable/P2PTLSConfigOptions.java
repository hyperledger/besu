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
package org.hyperledger.besu.cli.options.stable;

import static java.util.Arrays.asList;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_KEYSTORE_TYPE;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;

import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

public class P2PTLSConfigOptions {
  @Option(
      names = {"--Xp2p-tls-enabled"},
      hidden = true,
      description = "Enable P2P TLS functionality (default: ${DEFAULT-VALUE})")
  private final Boolean p2pTLSEnabled = false;

  @SuppressWarnings({
    "FieldCanBeFinal",
    "FieldMayBeFinal"
  }) // p2pTLSKeyStoreType requires non-final Strings.
  @Option(
      names = {"--Xp2p-tls-keystore-type"},
      hidden = true,
      paramLabel = "<NAME>",
      description = "P2P service keystore type. Required if P2P TLS is enabled.")
  private String p2pTLSKeyStoreType = DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--Xp2p-tls-keystore-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Keystore containing key/certificate for the P2P service.")
  private final Path p2pTLSKeyStoreFile = null;

  @Option(
      names = {"--Xp2p-tls-keystore-password-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "File containing password to unlock keystore for the P2P service. Required if P2P TLS is enabled.")
  private final Path p2pTLSKeyStorePasswordFile = null;

  @SuppressWarnings({
    "FieldCanBeFinal",
    "FieldMayBeFinal"
  }) // p2pTLSTrustStoreType requires non-final Strings.
  @Option(
      names = {"--Xp2p-tls-truststore-type"},
      hidden = true,
      paramLabel = "<NAME>",
      description = "P2P service truststore type.")
  private String p2pTLSTrustStoreType = DEFAULT_KEYSTORE_TYPE;

  @Option(
      names = {"--Xp2p-tls-truststore-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Truststore containing trusted certificates for the P2P service.")
  private final Path p2pTLSTrustStoreFile = null;

  @Option(
      names = {"--Xp2p-tls-truststore-password-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "File containing password to unlock truststore for the P2P service.")
  private final Path p2pTLSTrustStorePasswordFile = null;

  @Option(
      names = {"--Xp2p-tls-crl-file"},
      hidden = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Certificate revocation list for the P2P service.")
  private final Path p2pCrlFile = null;

  public Optional<TLSConfiguration> p2pTLSConfiguration(final CommandLine commandLine) {
    if (!p2pTLSEnabled) {
      return Optional.empty();
    }

    if (p2pTLSKeyStoreType == null) {
      throw new ParameterException(
          commandLine, "Keystore type is required when p2p TLS is enabled");
    }

    if (p2pTLSKeyStorePasswordFile == null) {
      throw new ParameterException(
          commandLine,
          "File containing password to unlock keystore is required when p2p TLS is enabled");
    }

    return Optional.of(
        TLSConfiguration.Builder.tlsConfiguration()
            .withKeyStoreType(p2pTLSKeyStoreType)
            .withKeyStorePath(p2pTLSKeyStoreFile)
            .withKeyStorePasswordSupplier(new FileBasedPasswordProvider(p2pTLSKeyStorePasswordFile))
            .withKeyStorePasswordPath(p2pTLSKeyStorePasswordFile)
            .withTrustStoreType(p2pTLSTrustStoreType)
            .withTrustStorePath(p2pTLSTrustStoreFile)
            .withTrustStorePasswordSupplier(
                null == p2pTLSTrustStorePasswordFile
                    ? null
                    : new FileBasedPasswordProvider(p2pTLSTrustStorePasswordFile))
            .withTrustStorePasswordPath(p2pTLSTrustStorePasswordFile)
            .withCrlPath(p2pCrlFile)
            .build());
  }

  public void checkP2PTLSOptionsDependencies(final Logger logger, final CommandLine commandLine) {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--Xp2p-tls-enabled",
        !p2pTLSEnabled,
        asList("--Xp2p-tls-keystore-type", "--Xp2p-tls-keystore-password-file"));
  }
}
