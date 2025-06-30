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
package org.hyperledger.besu.plugin.data;

/**
 * Plugin interface representing a client capability.
 * This interface provides plugin access to capability functionality.
 */
public interface Capability {

  /**
   * Create a new capability with the given name and version.
   *
   * @param name the capability name
   * @param version the capability version
   * @return a new capability instance
   */
  static Capability create(final String name, final int version) {
    return new CapabilityImpl(name, version);
  }

  /**
   * Get the capability name.
   *
   * @return the capability name
   */
  String getName();

  /**
   * Get the capability version.
   *
   * @return the capability version
   */
  int getVersion();

  /**
   * Default implementation of Capability.
   */
  class CapabilityImpl implements Capability {
    private final String name;
    private final int version;

    private CapabilityImpl(final String name, final int version) {
      this.name = name;
      this.version = version;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int getVersion() {
      return version;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final CapabilityImpl that = (CapabilityImpl) o;
      return version == that.version && java.util.Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, version);
    }

    @Override
    public String toString() {
      return name + "/" + version;
    }
  }
}
