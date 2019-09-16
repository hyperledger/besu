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
package org.hyperledger.besu.cli.subcommands;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.PasswordSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand.HashSubCommand;

import java.io.PrintStream;

import org.springframework.security.crypto.bcrypt.BCrypt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = COMMAND_NAME,
    description = "This command provides password related actions.",
    mixinStandardHelpOptions = true,
    subcommands = {HashSubCommand.class})
public class PasswordSubCommand implements Runnable {

  public static final String COMMAND_NAME = "password";

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  final PrintStream out;

  public PasswordSubCommand(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  @Command(
      name = "hash",
      description = "This command generates the hash of a given password.",
      mixinStandardHelpOptions = true)
  static class HashSubCommand implements Runnable {

    @SuppressWarnings("FieldMustBeFinal")
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

      parentCommand.out.print(BCrypt.hashpw(password, BCrypt.gensalt()));
    }
  }
}
