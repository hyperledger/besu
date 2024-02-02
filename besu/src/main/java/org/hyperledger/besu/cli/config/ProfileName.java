/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.config;

import org.apache.commons.lang3.StringUtils;

/** Enum for profile names. Each profile corresponds to a configuration file. */
public enum ProfileName {

  /** The 'MINIMALIST_STAKER' profile */
  MINIMALIST_STAKER("profiles/minimalist-staker.toml"),
  /** The 'DEV' profile. Corresponds to the 'profiles/dev.toml' configuration file. */
  DEV("profiles/dev.toml");

  private final String configFile;

  /**
   * Constructs a new ProfileName.
   *
   * @param configFile the configuration file corresponding to the profile
   */
  ProfileName(final String configFile) {
    this.configFile = configFile;
  }

  /**
   * Gets the configuration file corresponding to the profile.
   *
   * @return the configuration file
   */
  public String getConfigFile() {
    return configFile;
  }

  @Override
  public String toString() {
    return StringUtils.capitalize(name().replaceAll("_", " ").toLowerCase());
  }
}
