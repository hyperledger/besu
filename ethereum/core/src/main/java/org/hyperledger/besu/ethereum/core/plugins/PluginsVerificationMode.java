/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.plugins;

import java.util.Arrays;

public enum PluginsVerificationMode {
  NONE(false, false, false, false),
  FULL(true, true, true, true);

  private final boolean failOnCatalogLess;
  private final boolean failOnBesuVersionConflict;
  private final boolean failOnBesuDependencyConflict;
  private final boolean failOnPluginDependencyConflict;

  PluginsVerificationMode(
      final boolean failOnCatalogLess,
      final boolean failOnBesuVersionConflict,
      final boolean failOnBesuDependencyConflict,
      final boolean failOnPluginDependencyConflict) {
    this.failOnCatalogLess = failOnCatalogLess;
    this.failOnBesuVersionConflict = failOnBesuVersionConflict;
    this.failOnBesuDependencyConflict = failOnBesuDependencyConflict;
    this.failOnPluginDependencyConflict = failOnPluginDependencyConflict;
  }

  public boolean failOnCatalogLess() {
    return failOnCatalogLess;
  }

  public boolean failOnBesuVersionConflict() {
    return failOnBesuVersionConflict;
  }

  public boolean failOnBesuDependencyConflict() {
    return failOnBesuDependencyConflict;
  }

  public boolean failOnPluginDependencyConflict() {
    return failOnPluginDependencyConflict;
  }

  public static PluginsVerificationMode valueOfIgnoreCase(final String name) {
    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No PluginVerificationMode for name " + name));
  }
}
