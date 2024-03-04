package org.hyperledger.besu.cli.converter;

import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class PluginInfoConverter implements CommandLine.ITypeConverter<List<PluginInfo>> {

  @Override
  public List<PluginInfo> convert(final String value) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(this::toPluginInfo).toList();
  }

  private PluginInfo toPluginInfo(final String pluginName) {
    return new PluginInfo(pluginName);
  }
}
