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
package org.hyperledger.besu.cli.options;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import io.vertx.core.json.JsonObject;
import org.hyperledger.besu.cli.CommandTestAbstract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

public abstract class AbstractCLIOptionsTest<D, T extends CLIOptions<D>>
    extends CommandTestAbstract {
  @Test
  public void fromDomainObject_default() {
    fromDomainObject(createDefaultDomainObject());
  }

  @Test
  public void fromDomainObject_customize() {
    fromDomainObject(createCustomizedDomainObject());
  }

  private void fromDomainObject(final D domainObject) {
    final T options = optionsFromDomainObject(domainObject);
    final D domainObjectFromOptions = options.toDomainObject();

    final List<String> fieldsToIgnore = getFieldsToIgnore();
    final String[] ignored = fieldsToIgnore.toArray(new String[0]);
    assertThat(domainObjectFromOptions)
        .usingRecursiveComparison()
        .ignoringFields(ignored)
        .isEqualTo(domainObject);
  }

  @Test
  public void getCLIOptions_default() {
    getCLIOptions(createDefaultDomainObject());
  }

  @Test
  public void getCLIOptions_custom() {
    getCLIOptions(createCustomizedDomainObject());
  }

  private void getCLIOptions(final D domainObject) {
    T options = optionsFromDomainObject(domainObject);
    final String[] cliOptions = options.getCLIOptions().toArray(new String[0]);

    final TestBesuCommand cmd = parseCommand(cliOptions);
    final T optionsFromCommand = getOptionsFromBesuCommand(cmd);

    assertThat(optionsFromCommand).usingRecursiveComparison().isEqualTo(options);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void defaultValues() {
    final TestBesuCommand cmd = parseCommand();

    final D defaultDomainObject = createDefaultDomainObject();
    final T defaultOptions = optionsFromDomainObject(defaultDomainObject);
    final T optionsFromCommand = getOptionsFromBesuCommand(cmd);

    // Check default values supplied by CLI match expected default values
    final String[] fieldsToIgnore = getFieldsWithComputedDefaults().toArray(new String[0]);
    assertThat(optionsFromCommand)
        .usingRecursiveComparison()
        .ignoringFields(fieldsToIgnore)
        .isEqualTo(defaultOptions);
  }

  protected abstract D createDefaultDomainObject();

  protected abstract D createCustomizedDomainObject();

  protected List<String> getFieldsWithComputedDefaults() {
    return Collections.emptyList();
  }

  protected List<String> getFieldsToIgnore() {
    return Collections.emptyList();
  }

  protected abstract T optionsFromDomainObject(D domainObject);

  protected abstract T getOptionsFromBesuCommand(final TestBesuCommand besuCommand);

  protected void internalTestSuccess(final Consumer<D> assertion, final String... args) {
    final TestBesuCommand cmd = parseCommand(args);

    final T options = getOptionsFromBesuCommand(cmd);
    final D config = options.toDomainObject();
    assertion.accept(config);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockControllerBuilder).build();
  }

  protected void internalTestFailure(final String errorMsg, final String... args) {
    parseCommand(args);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).contains(errorMsg);
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param dependentOptions the string representing the list of dependent options names
   * @param mainOption the main option name
   */
  protected void verifyOptionsConstraintLoggerCall(
          final String mainOption, final String... dependentOptions) {
    verify(mockLogger, atLeast(1))
            .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues().get(0)).isEqualTo(DEPENDENCY_WARNING_MSG);

    for (final String option : dependentOptions) {
      assertThat(stringArgumentCaptor.getAllValues().get(1)).contains(option);
    }

    assertThat(stringArgumentCaptor.getAllValues().get(2)).isEqualTo(mainOption);
  }

}
