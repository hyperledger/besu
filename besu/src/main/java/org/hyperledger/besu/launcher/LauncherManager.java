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
package org.hyperledger.besu.launcher;

import static de.codeshelf.consoleui.elements.ConfirmChoice.ConfirmationValue.YES;

import org.hyperledger.besu.launcher.exception.LauncherException;
import org.hyperledger.besu.launcher.model.Step;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import de.codeshelf.consoleui.elements.ConfirmChoice.ConfirmationValue;
import de.codeshelf.consoleui.prompt.CheckboxResult;
import de.codeshelf.consoleui.prompt.ConfirmResult;
import de.codeshelf.consoleui.prompt.ConsolePrompt;
import de.codeshelf.consoleui.prompt.InputResult;
import de.codeshelf.consoleui.prompt.ListResult;
import de.codeshelf.consoleui.prompt.PromtResultItemIF;
import de.codeshelf.consoleui.prompt.builder.CheckboxPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.InputValueBuilder;
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.PromptBuilder;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

@SuppressWarnings({"unchecked"})
public class LauncherManager {

  private static final String CONFIG_FILE_NAME = "config.toml";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final List<Object> commandClass;

  private String configFileKey;

  private final Map<String, String> additionalFlag;

  public LauncherManager(final Object... commandClass) {
    this.commandClass = Arrays.asList(commandClass);
    this.additionalFlag = new HashMap<>();
  }

  public File run() throws LauncherException {
    AnsiConsole.systemInstall();
    try {
      final String script =
          new String(
              LauncherManager.class.getResourceAsStream("launcher.json").readAllBytes(),
              Charsets.UTF_8);
      final Map<String, PromtResultItemIF> configuration = new HashMap<>();
      final Step[] steps = MAPPER.readValue(script, Step[].class);
      for (Step stepFound : steps) {
        configuration.putAll(createInput(stepFound));
      }
      return createConfigFile(configuration);
    } catch (Exception e) {
      throw new LauncherException(e.getMessage());
    }
  }

  private Map<String, PromtResultItemIF> createInput(final Step step) throws LauncherException {
    try {
      final ConsolePrompt prompt = new ConsolePrompt();
      final PromptBuilder promptBuilder = prompt.getPromptBuilder();
      if (step.isConfigFileLocation()) {
        configFileKey = step.getConfigKey();
      }
      switch (step.getPromptType()) {
        case LIST:
          additionalFlag.putAll(step.getAdditionalFlag());
          return processList(prompt, promptBuilder, step);
        case CHECKBOX:;
          return processCheckbox(prompt, promptBuilder, step);
        case INPUT:
          return processInput(prompt, promptBuilder, step);
        case CONFIRM:
          return processConfirm(prompt, promptBuilder, step);
        default:
          throw new LauncherException("invalid input type");
      }
    } catch (Exception e) {
      throw new LauncherException("error during launcher creation : " + e.getMessage());
    }
  }

  private Map<String, PromtResultItemIF> processList(
      final ConsolePrompt prompt, final PromptBuilder promptBuilder, final Step step)
      throws LauncherException {
    final ListPromptBuilder list = promptBuilder.createListPrompt();
    list.name(step.getConfigKey()).message(step.getQuestion());
    try {
      formatOptions(step)
          .forEach(value -> list.newItem().text(value.toString().toLowerCase()).add());
      list.addPrompt();
      return (Map<String, PromtResultItemIF>) prompt.prompt(promptBuilder.build());
    } catch (Exception e) {
      throw new LauncherException("invalid default option for " + step.getConfigKey());
    }
  }

  private Map<String, PromtResultItemIF> processCheckbox(
      final ConsolePrompt prompt, final PromptBuilder promptBuilder, final Step step)
      throws LauncherException, IOException {
    final CheckboxPromptBuilder checkbox = promptBuilder.createCheckboxPrompt();
    checkbox.name(step.getConfigKey()).message(step.getQuestion());
    formatOptions(step)
        .forEach(value -> checkbox.newItem().text(value.toString().toLowerCase()).add());
    checkbox.addPrompt();
    return (Map<String, PromtResultItemIF>) prompt.prompt(promptBuilder.build());
  }

  private Map<String, PromtResultItemIF> processInput(
      final ConsolePrompt prompt, final PromptBuilder promptBuilder, final Step step)
      throws IOException {
    final InputValueBuilder inputPrompt = promptBuilder.createInputPrompt();
    setDefaultValue(step.getConfigKey(), inputPrompt);
    inputPrompt.name(step.getConfigKey()).message(step.getQuestion()).addPrompt();
    return (Map<String, PromtResultItemIF>) prompt.prompt(promptBuilder.build());
  }

  private Map<String, PromtResultItemIF> processConfirm(
      final ConsolePrompt prompt, final PromptBuilder promptBuilder, final Step step)
      throws IOException, LauncherException {
    final Map<String, PromtResultItemIF> configuration = new HashMap<>();
    final String name =
        Optional.ofNullable(step.getConfigKey()).orElse(Long.toString(System.nanoTime()));
    promptBuilder
        .createConfirmPromp()
        .name(name)
        .message(step.getQuestion())
        .defaultValue(ConfirmationValue.valueOf(step.getDefaultOption().toUpperCase()))
        .addPrompt();
    final HashMap<String, ? extends PromtResultItemIF> result =
        prompt.prompt(promptBuilder.build());
    final ConfirmationValue confirmed = ((ConfirmResult) result.get(name)).getConfirmed();
    if (step.getConfigKey() != null && !step.getConfigKey().isEmpty()) {
      configuration.putAll(result);
    }
    if (confirmed.equals(YES)) {
      for (Step subStep : step.getSubQuestions()) {
        configuration.putAll(createInput(subStep));
      }
    }
    return configuration;
  }

  private List<Object> formatOptions(final Step step) throws LauncherException {
    try {
      final List<String> split = Splitter.on('$').splitToList(step.getAvailableOptions());
      if (split.size() > 1) {
        return (List<Object>) Class.forName(split.get(0)).getField(split.get(1)).get(null);
      } else {
        return Arrays.asList(Class.forName(step.getAvailableOptions()).getEnumConstants());
      }
    } catch (Exception e) {
      throw new LauncherException("invalid default option for " + step.getConfigKey());
    }
  }

  private void setDefaultValue(final String key, final InputValueBuilder inputPrompt) {
    try {
      for (Object o : commandClass) {
        for (Field f : o.getClass().getDeclaredFields()) {
          if (f.isAnnotationPresent(CommandLine.Option.class)) {
            final CommandLine.Option annotation = f.getAnnotation(CommandLine.Option.class);
            if (Arrays.toString(annotation.names()).contains(key)) {
              f.setAccessible(true);
              inputPrompt.defaultValue(f.get(o).toString());
              break;
            }
          }
        }
      }
    } catch (Exception e) {
      // ignore
    }
  }

  private File createConfigFile(final Map<String, PromtResultItemIF> configuration)
      throws LauncherException {
    final StringBuilder config = new StringBuilder();

    String dataDir = null;
    for (Map.Entry<String, ? extends PromtResultItemIF> entry : configuration.entrySet()) {
      String key = entry.getKey();
      PromtResultItemIF value = entry.getValue();
      if (value instanceof ConfirmResult) {
        config.append(
            String.format(
                "%s=%s%n", key, ((ConfirmResult) value).getConfirmed() == YES ? "true" : "false"));
      } else if (value instanceof InputResult) {
        String input = ((InputResult) value).getInput();
        config.append(String.format("%s=\"%s\"%n", key, input));
        if (key.equals(configFileKey)) {
          dataDir = input;
        }
      } else if (value instanceof CheckboxResult) {
        config.append(
            String.format(
                "%s=%s%n",
                key,
                ((CheckboxResult) value)
                    .getSelectedIds().stream()
                        .map(String::toUpperCase)
                        .map(elt -> String.format("\"%s\"", elt))
                        .collect(Collectors.toList())));
      } else if (value instanceof ListResult) {
        final String selectedItem = ((ListResult) value).getSelectedId();
        if (additionalFlag.containsKey(selectedItem)) {
          config.append(String.format("%s%n", additionalFlag.get(selectedItem)));
        }
        config.append(String.format("%s=\"%s\"%n", key, selectedItem.toUpperCase()));
      }
    }
    if (dataDir == null) {
      throw new LauncherException("invalid launcher script : missing config file location");
    }
    final File file = new File(dataDir + File.separator + CONFIG_FILE_NAME);
    try (final PrintWriter out = new PrintWriter(file, Charsets.UTF_8)) {
      out.print(config.toString());
    } catch (Exception e) {
      throw new LauncherException(String.format("error creating config file :%s", e.getMessage()));
    }
    return file;
  }
}
