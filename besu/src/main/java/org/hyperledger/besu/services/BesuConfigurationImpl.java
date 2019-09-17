/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.BesuConfiguration;

import java.nio.file.Path;

public class BesuConfigurationImpl implements BesuConfiguration {

  private final Path storagePath;

  public BesuConfigurationImpl(final Path storagePath) {
    this.storagePath = storagePath;
  }

  @Override
  public Path getStoragePath() {
    return storagePath;
  }
}
