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
package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.cli.util.EnvironmentVariableDefaultProvider;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import picocli.CommandLine.Model.OptionSpec;

public class EnvironmentVariableDefaultProviderTest {

  private final Map<String, String> environment = new HashMap<>();

  private final EnvironmentVariableDefaultProvider provider =
      new EnvironmentVariableDefaultProvider(environment);

  @Test
  public void shouldReturnNullWhenEnvironmentVariableIsNotSet() {
    assertThat(provider.defaultValue(OptionSpec.builder("--no-env-var-set").build())).isNull();
  }

  @Test
  public void shouldReturnValueWhenEnvironmentVariableIsSet() {
    environment.put("PANTHEON_ENV_VAR_SET", "abc");
    assertThat(provider.defaultValue(OptionSpec.builder("--env-var-set").build())).isEqualTo("abc");
  }

  @Test
  public void shouldReturnValueWhenEnvironmentVariableIsSetForAlternateName() {
    environment.put("PANTHEON_ENV_VAR_SET", "abc");
    assertThat(provider.defaultValue(OptionSpec.builder("--env-var", "--env-var-set").build()))
        .isEqualTo("abc");
  }

  @Test
  public void shouldNotReturnValueForShortOptions() {
    environment.put("PANTHEON_H", "abc");
    assertThat(provider.defaultValue(OptionSpec.builder("-h").build())).isNull();
  }
}
