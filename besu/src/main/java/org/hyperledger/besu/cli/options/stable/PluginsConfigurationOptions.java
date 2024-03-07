package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME;

import org.hyperledger.besu.cli.converter.PluginInfoConverter;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;

import picocli.CommandLine;

public class PluginsConfigurationOptions {

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_OPTION_NAME},
      description = "Comma separated list of plugins.",
      split = ",",
      converter = PluginInfoConverter.class,
      arity = "0..*")
  @SuppressWarnings({
    "FieldCanBeFinal",
    "FieldMayBeFinal",
    "UnusedVariable"
  }) // PicoCLI requires non-final Strings.
  private List<PluginInfo> plugins = null;

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME},
      defaultValue = "false",
      description = "Comma separated list of plugins.")
  @SuppressWarnings({
    "FieldCanBeFinal",
    "FieldMayBeFinal",
    "UnusedVariable"
  }) // PicoCLI requires non-final Strings.
  private boolean requireExplicitPlugins = false;

  public static PluginConfiguration fromCommandLine(final CommandLine commandLine) {
    PluginConfiguration.DetectionType detectionType =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine,
            DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME,
            PluginConfiguration.DetectionType::valueOf);

    List<PluginInfo> plugins =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_OPTION_NAME, new PluginInfoConverter());

    return new PluginConfiguration(
        plugins != null ? plugins : List.of(),
        detectionType,
        PluginConfiguration.defaultPluginsDir());
  }
}
