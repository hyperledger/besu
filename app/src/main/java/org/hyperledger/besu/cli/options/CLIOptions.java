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
package org.hyperledger.besu.cli.options;

import java.util.List;

/**
 * This interface represents logic that translates between CLI options and a domain object.
 *
 * @param <T> A class to be constructed from CLI arguments.
 */
public interface CLIOptions<T> {

  /**
   * Transform CLI options into a domain object.
   *
   * @return A domain object representing these CLI options.
   */
  T toDomainObject();

  /**
   * Return The list of CLI options corresponding to this class.
   *
   * @return The list of CLI options corresponding to this class.
   */
  List<String> getCLIOptions();
}
