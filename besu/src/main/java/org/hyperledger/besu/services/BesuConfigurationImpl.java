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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.BesuConfiguration;

import java.nio.file.Path;
import java.util.Optional;

/** A concrete implementation of BesuConfiguration which is used in Besu plugin framework. */
public class BesuConfigurationImpl implements BesuConfiguration {

  private final Path storagePath;
  private final Path dataPath;
  private final Optional<String> rpcHttpHost;

  private final Optional<Integer> rpcHttpPort;

  public BesuConfigurationImpl(final Path storagePath, final Path dataPath) {
    this.rpcHttpHost = Optional.empty();
    this.rpcHttpPort = Optional.empty();
    this.storagePath = storagePath;
    this.dataPath = dataPath;
  }

  public BesuConfigurationImpl(
      final String rpcHttpHost,
      final int rpcHttpPort,
      final Path storagePath,
      final Path dataPath) {
    this.rpcHttpHost = Optional.of(rpcHttpHost);
    this.rpcHttpPort = Optional.of(rpcHttpPort);
    this.storagePath = storagePath;
    this.dataPath = dataPath;
  }

  @Override
  public Optional<String> getRpcHttpHost() {
    return rpcHttpHost;
  }

  @Override
  public Optional<Integer> getRpcHttpPort() {
    return rpcHttpPort;
  }

  @Override
  public Path getStoragePath() {
    return storagePath;
  }

  @Override
  public Path getDataPath() {
    return dataPath;
  }
}
