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
 *
 */

package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;

import java.nio.file.Path;
import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@SuppressWarnings("WeakerAccess")
@Module
public class EvmToolCommandOptionsModule {

  @Option(
      names = {"--revert-reason-enabled"},
      paramLabel = "<Boolean>",
      description = "Should revert reasons be persisted. (default: ${FALLBACK-VALUE})",
      arity = "0..1",
      fallbackValue = "true")
  final Boolean revertReasonEnabled = true;

  @Provides
  @Named("RevertReasonEnabled")
  boolean provideRevertReasonEnabled() {
    return revertReasonEnabled;
  }

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--key-value-storage"},
      description =
          "Identity for the key-value storage to be used (default: 'memory' alternate: 'rocksdb')",
      arity = "1")
  private String keyValueStorageName = "memory";

  @Provides
  @Named("KeyValueStorageName")
  String provideKeyValueStorageName() {
    return keyValueStorageName;
  }

  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = "<PATH>",
      description =
          "If using RocksDB storage, the path to Besu data directory (default: ${DEFAULT-VALUE})")
  final Path dataPath = getDefaultBesuDataPath(this);

  @Provides
  BesuConfiguration provideBesuConfiguration() {
    return new BesuConfigurationImpl(dataPath, dataPath.resolve(BesuController.DATABASE_PATH));
  }

  @Option(
      names = {"--block-number"},
      description =
          "Block number to evaluate against (default: 'PENDING', or 'EARLIEST', 'LATEST', or a number)",
      arity = "1")
  private final BlockParameter blockParameter = BlockParameter.PENDING;

  @Provides
  BlockParameter provideBlockParameter() {
    return blockParameter;
  }
}
