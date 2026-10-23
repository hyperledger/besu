/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql;

import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * The command line options of the GraphQL plugin, registered as a mixin of Besu's command. The
 * options are in an argument group so that the help shows them in their own "GraphQL Options"
 * section, as before the GraphQL service became a plugin.
 */
public class GraphQLCliOptions {

  // The spec picocli builds for this object, whose options belong to Besu's command once they are
  // added to it.
  @CommandLine.Spec private CommandSpec spec;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI may replace the group object
  @CommandLine.ArgGroup(validate = false, heading = "@|bold GraphQL Options|@%n")
  private GraphQlOptions graphQlOptions = new GraphQlOptions();

  /** Default constructor */
  public GraphQLCliOptions() {}

  /**
   * Returns the GraphQL options. Picocli replaces the options object when it parses a GraphQL
   * option, so callers must not keep the returned object.
   *
   * @return the GraphQL options
   */
  public GraphQlOptions getGraphQlOptions() {
    return graphQlOptions;
  }

  /**
   * Returns the command line of the command these options were added to.
   *
   * @return the command line, or null if the options were not added to a command
   */
  public CommandLine getCommandLine() {
    if (spec == null) {
      return null;
    }
    return spec.argGroups().stream()
        .flatMap(group -> group.args().stream())
        .map(ArgSpec::command)
        .filter(command -> command != null && command != spec)
        .map(CommandSpec::commandLine)
        .findFirst()
        .orElse(null);
  }
}
