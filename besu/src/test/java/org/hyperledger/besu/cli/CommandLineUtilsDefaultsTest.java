package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.util.CommandLineUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@link CommandLineUtils} focusing on the retrieval of option values
 * (getOptionValueOrDefault).
 */
public class CommandLineUtilsDefaultsTest {
  private CommandLine commandLine;
  private CommandLine.ParseResult parseResult;
  private CommandLine.Model.CommandSpec commandSpec;
  private CommandLine.Model.OptionSpec optionSpec;

  private CommandLine.IDefaultValueProvider defaultValueProvider;

  @BeforeEach
  public void setUp() {
    commandLine = mock(CommandLine.class);
    parseResult = mock(CommandLine.ParseResult.class);
    commandSpec = mock(CommandLine.Model.CommandSpec.class);
    optionSpec = mock(CommandLine.Model.OptionSpec.class);
    defaultValueProvider = mock(CommandLine.IDefaultValueProvider.class);

    when(commandLine.getParseResult()).thenReturn(parseResult);
    when(commandLine.getCommandSpec()).thenReturn(commandSpec);
    when(commandLine.getDefaultValueProvider()).thenReturn(defaultValueProvider);
  }

  @Test
  public void testGetOptionValueOrDefault_UserProvidedValue() {
    when(parseResult.matchedOptionValue("option", null)).thenReturn("optionValue");
    String result =
        CommandLineUtils.getOptionValueOrDefault(commandLine, "option", String::valueOf);
    assertThat(result).isEqualTo("optionValue");
  }

  @Test
  public void testGetOptionValueOrDefault_DefaultValue() throws Exception {
    when(commandSpec.findOption("option")).thenReturn(optionSpec);
    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn("defaultValue");
    String result =
        CommandLineUtils.getOptionValueOrDefault(commandLine, "option", String::valueOf);
    assertThat(result).isEqualTo("defaultValue");
  }

  @Test
  public void userOptionOverridesDefaultValue() throws Exception {
    when(parseResult.matchedOptionValue("option", null)).thenReturn("optionValue");
    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn("defaultValue");
    String result =
        CommandLineUtils.getOptionValueOrDefault(commandLine, "option", String::valueOf);
    assertThat(result).isEqualTo("optionValue");
  }

  @Test
  public void testGetOptionValueOrDefault_NoValueOrDefault() {
    when(commandSpec.findOption("option")).thenReturn(null);
    String result =
        CommandLineUtils.getOptionValueOrDefault(commandLine, "option", String::valueOf);
    assertThat(result).isNull();
  }

  @Test
  public void testGetOptionValueOrDefault_ConversionFailure() throws Exception {
    when(commandSpec.findOption("option")).thenReturn(optionSpec);
    when(commandLine.getDefaultValueProvider()).thenReturn(defaultValueProvider);
    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn("defaultValue");

    CommandLine.ITypeConverter<Integer> failingConverter =
        value -> {
          throw new Exception("Conversion failed");
        };

    String actualMessage =
        assertThrows(
                RuntimeException.class,
                () ->
                    CommandLineUtils.getOptionValueOrDefault(
                        commandLine, "option", failingConverter))
            .getMessage();
    final String expectedMessage =
        "Failed to convert default value for option option: Conversion failed";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }
}
