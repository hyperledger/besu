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

import org.hyperledger.besu.cli.CommandTestAbstract;

import java.util.Collections;
import java.util.List;

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
    assertThat(domainObjectFromOptions).isEqualToIgnoringGivenFields(domainObject, ignored);
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
    assertThat(optionsFromCommand).isEqualToIgnoringGivenFields(defaultOptions, fieldsToIgnore);
  }

  abstract D createDefaultDomainObject();

  abstract D createCustomizedDomainObject();

  protected List<String> getFieldsWithComputedDefaults() {
    return Collections.emptyList();
  }

  protected List<String> getFieldsToIgnore() {
    return Collections.emptyList();
  }

  abstract T optionsFromDomainObject(D domainObject);

  abstract T getOptionsFromBesuCommand(final TestBesuCommand besuCommand);
}
