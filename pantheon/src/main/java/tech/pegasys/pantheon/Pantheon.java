/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.cli.PantheonCommand;
import tech.pegasys.pantheon.cli.PantheonControllerBuilder;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.util.BlockImporter;

import picocli.CommandLine.RunLast;

public final class Pantheon {
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int ERROR_EXIT_CODE = 1;

  public static void main(final String... args) {

    final PantheonCommand pantheonCommand =
        new PantheonCommand(
            getLogger(),
            new BlockImporter(),
            new RunnerBuilder(),
            new PantheonControllerBuilder(),
            new SynchronizerConfiguration.Builder(),
            EthereumWireProtocolConfiguration.builder(),
            new RocksDbConfiguration.Builder());

    pantheonCommand.parse(
        new RunLast().andExit(SUCCESS_EXIT_CODE),
        pantheonCommand.exceptionHandler().andExit(ERROR_EXIT_CODE),
        System.in,
        args);
  }
}
