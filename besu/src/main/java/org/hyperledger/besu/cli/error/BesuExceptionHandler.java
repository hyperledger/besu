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

import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigrationException;

import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public class BesuExceptionHandler
    extends CommandLine.AbstractHandler<List<Object>, BesuExceptionHandler>
    implements CommandLine.IExceptionHandler2<List<Object>> {

  private final Supplier<Level> levelSupplier;

  public BesuExceptionHandler(final Supplier<Level> levelSupplier) {
    this.levelSupplier = levelSupplier;
  }

  @Override
  public List<Object> handleParseException(
      final CommandLine.ParameterException ex, final String[] args) {
    final Level logLevel = levelSupplier.get();
    if (logLevel != null && Level.DEBUG.isMoreSpecificThan(logLevel)) {
      ex.printStackTrace(err());
    } else {
      err().println(ex.getMessage());
    }
    if (shouldPrintUsage(ex)) {
      ex.getCommandLine().usage(err(), ansi());
    }
    return returnResultOrExit(null);
  }

  private boolean shouldPrintUsage(final ParameterException ex) {
    return !CommandLine.UnmatchedArgumentException.printSuggestions(ex, err())
        && !(ex.getCause() instanceof PrivateStorageMigrationException);
  }

  @Override
  public List<Object> handleExecutionException(
      final CommandLine.ExecutionException ex, final CommandLine.ParseResult parseResult) {
    return throwOrExit(ex);
  }

  @Override
  protected BesuExceptionHandler self() {
    return this;
  }
}
