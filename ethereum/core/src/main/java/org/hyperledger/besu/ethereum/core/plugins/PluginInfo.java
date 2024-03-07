package org.hyperledger.besu.ethereum.core.plugins;
/** Represents information about a plugin, including its name. */
public record PluginInfo(String name) {
  /**
   * Constructs a new PluginInfo instance with the specified name.
   *
   * @param name The name of the plugin. Cannot be null or empty.
   * @throws IllegalArgumentException if the name is null or empty.
   */
  public PluginInfo {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Plugin name cannot be null or empty.");
    }
  }

  /**
   * Returns the name of the plugin.
   *
   * @return The name of the plugin.
   */
  @Override
  public String name() {
    return name;
  }

  // Optionally, override toString, equals, and hashCode methods

  @Override
  public String toString() {
    return "PluginInfo{name='" + name + "'}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PluginInfo that = (PluginInfo) o;

    return name.equals(that.name);
  }
}
