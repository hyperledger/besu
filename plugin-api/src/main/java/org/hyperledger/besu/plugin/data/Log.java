/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.plugin.data;

import org.hyperledger.besu.plugin.Unstable;

import java.util.List;

/** A Log entry from a transaction execution. */
@Unstable
public interface Log {

  /**
   * The address of the contract writing this log message.
   *
   * @return The loggers address.
   */
  Address getLogger();

  /**
   * The list of 32 byte log topics, possibly empty.
   *
   * @return The list, possibly zero length, of log topics.
   */
  List<? extends UnformattedData> getTopics();

  /**
   * The data, of possibly unlimited length, for this log entry.
   *
   * @return The log data.
   */
  UnformattedData getData();
}
