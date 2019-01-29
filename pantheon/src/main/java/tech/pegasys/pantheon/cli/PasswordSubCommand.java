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
package tech.pegasys.pantheon.cli;

import static tech.pegasys.pantheon.cli.PasswordSubCommand.COMMAND_NAME;

import java.io.PrintStream;

import org.springframework.security.crypto.bcrypt.BCrypt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
  name = COMMAND_NAME,
  description = "This command generates the hash of a given password.",
  mixinStandardHelpOptions = true
)
class PasswordSubCommand implements Runnable {

  static final String COMMAND_NAME = "password-hash";

  @SuppressWarnings("unused")
  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  final PrintStream out;

  @SuppressWarnings("FieldMustBeFinal")
  @Parameters(arity = "1..1", description = "The password input")
  private String password = null;

  PasswordSubCommand(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    out.print(BCrypt.hashpw(password, BCrypt.gensalt()));
  }
}
