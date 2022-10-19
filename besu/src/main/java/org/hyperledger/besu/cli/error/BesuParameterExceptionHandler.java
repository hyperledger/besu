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
package org.hyperledger.besu.cli.error;

import java.io.PrintWriter;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public class BesuParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {

  private final Supplier<Level> levelSupplier;

  public BesuParameterExceptionHandler(final Supplier<Level> levelSupplier) {
    this.levelSupplier = levelSupplier;
  }

  @Override
  public int handleParseException(final CommandLine.ParameterException ex, final String[] args) {
    final CommandLine cmd = ex.getCommandLine();
    final PrintWriter err = cmd.getErr();
    final Level logLevel = levelSupplier.get();
    if (logLevel != null && Level.DEBUG.isMoreSpecificThan(logLevel)) {
      ex.printStackTrace(err);
    } else {
      err.println(ex.getMessage());
    }

    CommandLine.UnmatchedArgumentException.printSuggestions(ex, err);

    // don't print full help, just the instructions required to get it
    err.println();
    err.println("To display full help:");
    err.println("besu [COMMAND] --help");

    final CommandSpec spec = cmd.getCommandSpec();

    return cmd.getExitCodeExceptionMapper() != null
        ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
        : spec.exitCodeOnInvalidInput();
  }
}
