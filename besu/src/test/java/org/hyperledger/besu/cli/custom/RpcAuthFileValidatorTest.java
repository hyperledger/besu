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
package org.hyperledger.besu.cli.custom;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RpcAuthFileValidatorTest {

  private static final String CORRECT_TOML = "/rpcauth/auth_correct.toml";
  private static final String DUPLICATE_USER_TOML = "/rpcauth/auth_duplicate_user.toml";
  private static final String INVALID_TOML = "/rpcauth/auth_invalid.toml";
  private static final String INVALID_GROUPS_VALUE_TOML = "/rpcauth/auth_invalid_groups_value.toml";
  private static final String INVALID_PERMISSIONS_VALUE_TOML =
      "/rpcauth/auth_invalid_permissions_value.toml";
  private static final String INVALID_PRIVACY_PUBLIC_KEY_VALUE_TOML =
      "/rpcauth/auth_invalid_privacy_public_key_value.toml";
  private static final String NO_PASSWORD_TOML = "/rpcauth/auth_no_password.toml";

  @Mock CommandLine commandLine;

  @Test
  public void shouldPassWhenCorrectTOML() {
    assertThatCode(
            () -> RpcAuthFileValidator.validate(commandLine, getFilePath(CORRECT_TOML), "HTTP"))
        .doesNotThrowAnyException();
  }

  @Test
  public void shouldFailWhenInvalidTOML() {
    assertThatThrownBy(
            () -> RpcAuthFileValidator.validate(commandLine, getFilePath(INVALID_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Invalid TOML configuration");
  }

  @Test
  public void shouldFailWhenMissingTOML() {
    assertThatThrownBy(
            () -> RpcAuthFileValidator.validate(commandLine, "thisshouldntexist", "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessage(
            "The specified RPC HTTP authentication credential file 'thisshouldntexist' does not exist");
  }

  @Test
  public void shouldFailWhenMissingPassword() {
    assertThatThrownBy(
            () -> RpcAuthFileValidator.validate(commandLine, getFilePath(NO_PASSWORD_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessage("RPC user specified without password.");
  }

  @Test
  public void shouldFailWhenInvalidGroupsKeyValue() {
    assertThatThrownBy(
            () ->
                RpcAuthFileValidator.validate(
                    commandLine, getFilePath(INVALID_GROUPS_VALUE_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessage("RPC authentication configuration file contains invalid values.");
  }

  @Test
  public void shouldFailWhenInvalidPermissionsKeyValue() {
    assertThatThrownBy(
            () ->
                RpcAuthFileValidator.validate(
                    commandLine, getFilePath(INVALID_PERMISSIONS_VALUE_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessage("RPC authentication configuration file contains invalid values.");
  }

  @Test
  public void shouldFailWhenInvalidEmptyPrivacyPublicKeyValue() {
    assertThatThrownBy(
            () ->
                RpcAuthFileValidator.validate(
                    commandLine, getFilePath(INVALID_PRIVACY_PUBLIC_KEY_VALUE_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessage("RPC authentication configuration file contains invalid values.");
  }

  @Test
  public void shouldFailWhenDuplicateUser() {
    assertThatThrownBy(
            () ->
                RpcAuthFileValidator.validate(
                    commandLine, getFilePath(DUPLICATE_USER_TOML), "HTTP"))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Invalid TOML configuration");
  }

  private String getFilePath(final String resourceName) {
    return this.getClass().getResource(resourceName).getPath();
  }
}
