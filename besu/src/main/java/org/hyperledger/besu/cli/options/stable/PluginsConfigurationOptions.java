package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME;

import org.hyperledger.besu.cli.converter.PluginInfoConverter;

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
  private List<PluginInfoConverter.PluginInfo> plugins = null;

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
}
