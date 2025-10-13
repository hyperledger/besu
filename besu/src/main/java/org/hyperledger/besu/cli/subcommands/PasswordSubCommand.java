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
package org.hyperledger.besu.cli.subcommands;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.PasswordSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand.HashSubCommand;
import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.PrintWriter;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.crypto.bcrypt.BCrypt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** The Password sub command. */
@Command(
    name = COMMAND_NAME,
    description = "This command provides password related actions.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {HashSubCommand.class})
public class PasswordSubCommand implements Runnable {

  /** The constant COMMAND_NAME. */
  public static final String COMMAND_NAME = "password";

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  private final PrintWriter out;

  /**
   * Instantiates a new Password sub command.
   *
   * @param out The PrintWriter where the usage will be reported.
   */
  public PasswordSubCommand(final PrintWriter out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /** The Hash sub command for password. */
  @Command(
      name = "hash",
      description = "This command generates the hash of a given password.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class HashSubCommand implements Runnable {

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @NotEmpty
    @Option(
        names = "--password",
        arity = "1..1",
        required = true,
        description = "The password input")
    private String password = null;

    @SuppressWarnings("unused")
    @ParentCommand
    private PasswordSubCommand parentCommand;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      parentCommand.out.println(BCrypt.hashpw(password, BCrypt.gensalt()));
    }
  }
}
