/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.converter;

/**
 * This interface can be used to give a converter the capability to format the converted value back
 * to its CLI form
 *
 * @param <V> the type of the CLI converted runtime value
 */
public interface TypeFormatter<V> {
  /**
   * Format a converted value back to its CLI form
   *
   * @param value the converted value
   * @return the textual CLI form of the value
   */
  String format(V value);
}
