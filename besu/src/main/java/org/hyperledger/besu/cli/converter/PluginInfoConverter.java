package org.hyperledger.besu.cli.converter;

import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import picocli.CommandLine;

/**
 * Converts a comma-separated string into a list of {@link PluginInfo} objects. This converter is
 * intended for use with PicoCLI to process command line arguments that specify plugin information.
 */
public class PluginInfoConverter implements CommandLine.ITypeConverter<List<PluginInfo>> {

  /**
   * Converts a comma-separated string into a list of {@link PluginInfo}.
   *
   * @param value The comma-separated string representing plugin names.
   * @return A list of {@link PluginInfo} objects created from the provided string.
   */
  @Override
  public List<PluginInfo> convert(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return List.of();
    }
    return Stream.of(value.split(","))
        .map(String::trim)
        .map(this::toPluginInfo)
        .collect(Collectors.toList());
  }

  /**
   * Creates a {@link PluginInfo} object from a plugin name.
   *
   * @param pluginName The name of the plugin.
   * @return A {@link PluginInfo} object representing the plugin.
   */
  private PluginInfo toPluginInfo(final String pluginName) {
    return new PluginInfo(pluginName.trim());
  }
}
