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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class PrivacyOptionsTest extends CommandTestAbstract {
  private static final String ENCLAVE_URI = "http://1.2.3.4:5555";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String ENCLAVE_PUBLIC_KEY_PATH =
      BesuCommand.class.getResource("/orion_publickey.pub").getPath();

  @Test
  public void privacyTlsOptionsRequiresTlsToBeEnabled() {
    when(storageService.getByName("rocksdb-privacy"))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");
    final String coinbaseStr = String.format("%040x", 1);

    parseCommand(
        "--privacy-enabled",
        "--miner-enabled",
        "--miner-coinbase=" + coinbaseStr,
        "--min-gas-price",
        "0",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--privacy-tls-keystore-file",
        "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-tls-enabled", "--privacy-tls-keystore-file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresTlsToBeEnabledToml() throws IOException {
    when(storageService.getByName("rocksdb-privacy"))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");
    final String coinbaseStr = String.format("%040x", 1);

    final Path toml =
        createTempFile(
            "toml",
            "privacy-enabled=true\n"
                + "miner-enabled=true\n"
                + "miner-coinbase=\""
                + coinbaseStr
                + "\"\n"
                + "min-gas-price=0\n"
                + "privacy-url=\""
                + ENCLAVE_URI
                + "\"\n"
                + "privacy-public-key-file=\""
                + configFile.getPath()
                + "\"\n"
                + "privacy-tls-keystore-file=\"/Users/me/key\"");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall("--privacy-tls-enabled", "--privacy-tls-keystore-file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresPrivacyToBeEnabled() {
    parseCommand("--privacy-tls-enabled", "--privacy-tls-keystore-file", "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-enabled", "--privacy-tls-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresPrivacyToBeEnabledToml() throws IOException {
    final Path toml =
        createTempFile(
            "toml", "privacy-tls-enabled=true\n" + "privacy-tls-keystore-file=\"/Users/me/key\"");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall("--privacy-enabled", "--privacy-tls-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void mustUseEnclaveUriAndOptions() {
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");

    parseCommand(
        "--privacy-enabled",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(true);
    assertThat(enclaveArg.getValue().getEnclaveUri()).isEqualTo(URI.create(ENCLAVE_URI));
    assertThat(enclaveArg.getValue().getPrivacyUserId()).isEqualTo(ENCLAVE_PUBLIC_KEY);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyOptionsRequiresServiceToBeEnabled() {

    final File file = new File("./specific/enclavePublicKey");
    file.deleteOnExit();

    parseCommand("--privacy-url", ENCLAVE_URI, "--privacy-public-key-file", file.toString());

    verifyMultiOptionsConstraintLoggerCall(
        "--privacy-url and/or --privacy-public-key-file ignored because none of --privacy-enabled was defined.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithFastSyncMustError() {
    parseCommand("--sync-mode=FAST", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Fast sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithSnapSyncMustError() {
    parseCommand("--sync-mode=SNAP", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Snap sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithCheckpointSyncMustError() {
    parseCommand("--sync-mode=CHECKPOINT", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Checkpoint sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithBonsaiDefaultMustError() {
    // bypass overridden parseCommand method which specifies bonsai
    super.parseCommand("--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Bonsai cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithBonsaiExplicitMustError() {
    // bypass overridden parseCommand method which specifies bonsai
    super.parseCommand("--privacy-enabled", "--data-storage-format", "BONSAI");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Bonsai cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithoutPrivacyPublicKeyFails() {
    parseCommand("--privacy-enabled", "--privacy-url", ENCLAVE_URI);

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Please specify Enclave public key file path to enable privacy");
  }

  @Test
  public void mustVerifyPrivacyIsDisabled() {
    parseCommand();

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(false);
  }

  @Test
  public void privacyMultiTenancyIsConfiguredWhenConfiguredWithNecessaryOptions() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(privacyParametersArgumentCaptor.getValue().isMultiTenancyEnabled()).isTrue();
  }

  @Test
  public void privacyMultiTenancyWithoutAuthenticationFails() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file");

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled");
  }

  @Test
  public void privacyMultiTenancyWithPrivacyPublicKeyFileFails() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH);

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Privacy multi-tenancy and privacy public key cannot be used together");
  }

  @Test
  public void flexiblePrivacyGroupEnabledFlagDefaultValueIsFalse() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isFlexiblePrivacyGroupsEnabled()).isEqualTo(false);
  }

  @Test
  public void flexiblePrivacyGroupEnabledFlagValueIsSet() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-flexible-groups-enabled",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isFlexiblePrivacyGroupsEnabled()).isEqualTo(true);
  }

  @Test
  public void privateMarkerTransactionSigningKeyFileRequiredIfMinGasPriceNonZero() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "1");

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified");
  }

  @Test
  public void
      privateMarkerTransactionSigningKeyFileNotRequiredIfMinGasPriceNonZeroAndUsingPluginPrivateMarkerTransactionFactory() {

    when(privacyPluginService.getPrivateMarkerTransactionFactory())
        .thenReturn(mock(PrivateMarkerTransactionFactory.class));

    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "1");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void
      privateMarkerTransactionSigningKeyFileNotCanNotBeUsedWithPluginPrivateMarkerTransactionFactory()
          throws IOException {
    when(privacyPluginService.getPrivateMarkerTransactionFactory())
        .thenReturn(mock(PrivateMarkerTransactionFactory.class));
    final Path toml =
        createTempFile(
            "key",
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63".getBytes(UTF_8));

    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-marker-transaction-signing-key-file",
        toml.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    //    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "--privacy-marker-transaction-signing-key-file can not be used in conjunction with a plugin that specifies");
  }

  @Test
  public void mustProvidePayloadWhenPrivacyPluginEnabled() {
    parseCommand("--privacy-enabled", "--Xprivacy-plugin-enabled", "--min-gas-price", "0");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "No Payload Provider has been provided. You must register one when enabling privacy plugin!");
  }

  @Test
  public void canNotUseFlexiblePrivacyWhenPrivacyPluginEnabled() {
    parseCommand(
        "--privacy-enabled",
        "--Xprivacy-plugin-enabled",
        "--min-gas-price",
        "0",
        "--privacy-flexible-groups-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "No Payload Provider has been provided. You must register one when enabling privacy plugin!");
  }

  @Test
  public void privEnclaveKeyFileDoesNotExist() {
    assumeTrue(
        System.getProperty("user.language").startsWith("en"),
        "Ignored if system language is not English");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", "/non/existent/file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Problem with privacy-public-key-file");
    assertThat(commandErrorOutput.toString(UTF_8)).contains("No such file");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentTooShort() throws IOException {
    final Path file = createTempFile("privacy.key", "lkjashdfiluhwelrk");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Last unit does not have enough valid bits");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentNotValidBase64() throws IOException {
    final Path file = createTempFile("privacy.key", "l*jashdfillk9ashdfillkjashdfillkjashdfilrtg=");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString(UTF_8)).contains("Illegal base64 character");
  }

  @Test
  public void privHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "PRIV", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "PRIV", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void eeaHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "EEA", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void eeaWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "EEA", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Override
  protected TestBesuCommand parseCommand(final String... args) {
    // privacy requires forest to be specified
    final List<String> argsPlusForest = new ArrayList<>(Arrays.stream(args).toList());
    argsPlusForest.add("--data-storage-format");
    argsPlusForest.add("FOREST");
    return super.parseCommand(argsPlusForest.toArray(String[]::new));
  }
}
