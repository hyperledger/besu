package org.hyperledger.besu.cli.converter;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class PluginInfoConverter
    implements CommandLine.ITypeConverter<List<PluginInfoConverter.PluginInfo>> {

  @Override
  public List<PluginInfo> convert(final String value) {
    return Arrays.stream(value.split(",")).map(this::toPluginInfo).toList();
  }

  private PluginInfo toPluginInfo(final String pluginName) {
    return new PluginInfo(pluginName);
  }

  public static class PluginInfo {
    String name;

    PluginInfo(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
