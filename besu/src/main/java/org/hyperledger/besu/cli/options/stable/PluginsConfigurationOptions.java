package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME;

import org.hyperledger.besu.cli.converter.PluginInfoConverter;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;

import picocli.CommandLine;

@SuppressWarnings("UnusedVariable")
public class PluginsConfigurationOptions {

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_OPTION_NAME},
      description = "Comma-separated list of plugins.",
      split = ",",
      converter = PluginInfoConverter.class,
      arity = "0..*")
  private List<PluginInfo> plugins;

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME},
      defaultValue = "false",
      description = "Enables strict registration of plugins.")
  private boolean strictRegistration;

  /**
   * Constructs a {@link PluginConfiguration} instance based on the command line options.
   *
   * @param commandLine The command line instance containing parsed options.
   * @return A new {@link PluginConfiguration} instance.
   */
  public static PluginConfiguration fromCommandLine(final CommandLine commandLine) {
    boolean strictRegistration =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME, Boolean::valueOf);

    List<PluginInfo> plugins =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_OPTION_NAME, new PluginInfoConverter());

    return new PluginConfiguration(
        plugins, strictRegistration, PluginConfiguration.defaultPluginsDir());
  }
}
