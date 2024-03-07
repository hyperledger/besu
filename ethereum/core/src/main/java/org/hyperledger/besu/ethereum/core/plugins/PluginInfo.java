package org.hyperledger.besu.ethereum.core.plugins;

/** Represents information about a plugin, including its name. */
public final class PluginInfo {
  private final String name;

  /**
   * Constructs a new PluginInfo instance with the specified name.
   *
   * @param name The name of the plugin. Cannot be null or empty.
   * @throws IllegalArgumentException if the name is null or empty.
   */
  public PluginInfo(final String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Plugin name cannot be null or empty.");
    }
    this.name = name;
  }

  public String name() {
    return name;
  }
}
