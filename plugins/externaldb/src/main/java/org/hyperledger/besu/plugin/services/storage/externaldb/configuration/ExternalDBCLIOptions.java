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
package org.hyperledger.besu.plugin.services.storage.externaldb.configuration;

import picocli.CommandLine;

public class ExternalDBCLIOptions {

  private static final String EXTERNALDB_ENDPOINT = "--plugin-externaldb-endpoint";

  @CommandLine.Option(
      names = {EXTERNALDB_ENDPOINT},
      description =
          "URL for the external database service. This cannot be used with the external db server.")
  String endpoint;

  private ExternalDBCLIOptions() {}

  public static ExternalDBCLIOptions create() {
    return new ExternalDBCLIOptions();
  }

  public ExternalDbConfiguration toDomainObject() {
    return new ExternalDbConfiguration(endpoint);
  }
}
