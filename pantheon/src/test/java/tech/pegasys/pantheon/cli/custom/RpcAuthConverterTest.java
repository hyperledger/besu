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
package tech.pegasys.pantheon.cli.custom;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Test;

public class RpcAuthConverterTest {

  private RpcAuthConverter rpcAuthConverter;
  private static final String CORRECT_TOML = "auth_correct.toml";
  private static final String DUPLICATE_USER_TOML = "auth_duplicate_user.toml";
  private static final String INVALID_TOML = "auth_invalid.toml";
  private static final String INVALID_VALUE_TOML = "auth_invalid_value.toml";
  private static final String NO_PASSWORD_TOML = "auth_no_password.toml";

  @Before
  public void setUp() {
    rpcAuthConverter = new RpcAuthConverter();
  }

  @Test
  public void shouldPassWhenCorrectTOML() {
    assertThatCode(() -> rpcAuthConverter.convert(getFilePath(CORRECT_TOML)))
        .doesNotThrowAnyException();
  }

  @Test
  public void shouldFailWhenInvalidTOML() {
    assertThatThrownBy(() -> rpcAuthConverter.convert(getFilePath(INVALID_TOML)))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("Invalid TOML configuration");
  }

  @Test
  public void shouldFailWhenMissingPassword() {
    assertThatThrownBy(() -> rpcAuthConverter.convert(getFilePath(NO_PASSWORD_TOML)))
        .isInstanceOf(Exception.class)
        .hasMessage("RPC user specified without password.");
  }

  @Test
  public void shouldFailWhenInvalidKeyValue() {
    assertThatThrownBy(() -> rpcAuthConverter.convert(getFilePath(INVALID_VALUE_TOML)))
        .isInstanceOf(Exception.class)
        .hasMessage("RPC authentication configuration file contains invalid values.");
  }

  @Test
  public void shouldFailWhenDuplicateUser() {
    assertThatThrownBy(() -> rpcAuthConverter.convert(getFilePath(DUPLICATE_USER_TOML)))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("Invalid TOML configuration");
  }

  private String getFilePath(final String resourceName) {
    return Resources.getResource(resourceName).getPath();
  }
}
