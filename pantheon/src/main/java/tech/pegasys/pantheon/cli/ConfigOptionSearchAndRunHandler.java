/*
 * Copyright 2018 ConsenSys AG.
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.DefaultExceptionHandler;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;

class ConfigOptionSearchAndRunHandler extends AbstractParseResultHandler<List<Object>> {

  private final AbstractParseResultHandler<List<Object>> resultHandler;
  private final DefaultExceptionHandler<List<Object>> exceptionHandler;
  private final String configFileOptionName;

  ConfigOptionSearchAndRunHandler(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String configFileOptionName) {
    this.resultHandler = resultHandler;
    this.exceptionHandler = exceptionHandler;
    this.configFileOptionName = configFileOptionName;
    // use the same output as the regular options handler to ensure that outputs are all going
    // the in the same place. No need to do this for the exception handler as we reuse it directly.
    this.useOut(resultHandler.out());
  }

  @Override
  protected List<Object> handle(final ParseResult parseResult) throws ExecutionException {
    final CommandLine commandLine = parseResult.asCommandLineList().get(0);
    if (parseResult.hasMatchedOption(configFileOptionName)) {
      final OptionSpec configFileOption = parseResult.matchedOption(configFileOptionName);
      final File configFile;
      try {
        configFile = configFileOption.getter().get();
      } catch (final Exception e) {
        throw new ExecutionException(commandLine, e.getMessage(), e);
      }
      final TomlConfigFileDefaultProvider tomlConfigFileDefaultProvider =
          new TomlConfigFileDefaultProvider(commandLine, configFile);
      commandLine.setDefaultValueProvider(tomlConfigFileDefaultProvider);
    }
    commandLine.parseWithHandlers(
        resultHandler, exceptionHandler, parseResult.originalArgs().toArray(new String[0]));
    return new ArrayList<>();
  }

  @Override
  protected ConfigOptionSearchAndRunHandler self() {
    return this;
  }
}
