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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;

import java.nio.file.Path;

/** A concrete implementation of BesuConfiguration which is used in Besu plugin framework. */
public class BesuConfigurationImpl implements BesuConfiguration {
  private Path storagePath;
  private Path dataPath;
  private DataStorageConfiguration dataStorageConfiguration;
  private MiningParameters miningParameters;

  /**
   * Post creation initialization
   *
   * @param dataPath The Path representing data folder
   * @param storagePath The path representing storage folder
   * @param dataStorageConfiguration The data storage configuration
   * @param miningParameters The mining parameters
   */
  public void init(
      final Path dataPath,
      final Path storagePath,
      final DataStorageConfiguration dataStorageConfiguration,
      final MiningParameters miningParameters) {
    this.dataPath = dataPath;
    this.storagePath = storagePath;
    this.dataStorageConfiguration = dataStorageConfiguration;
    this.miningParameters = miningParameters;
  }

  @Override
  public Path getStoragePath() {
    return storagePath;
  }

  @Override
  public Path getDataPath() {
    return dataPath;
  }

  @Override
  public int getDatabaseVersion() {
    return dataStorageConfiguration.getDataStorageFormat().getDatabaseVersion();
  }

  @Override
  public Wei getMinGasPrice() {
    return miningParameters.getMinTransactionGasPrice();
  }
}
