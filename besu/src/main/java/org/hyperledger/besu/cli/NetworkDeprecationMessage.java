/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.util.log.FramedLogMessage;

import java.util.List;

public class NetworkDeprecationMessage {

  public static String generate(final NetworkName network) {
    if (network.getDeprecationDate().isEmpty()) {
      throw new AssertionError("Deprecation date is not set. Cannot print a deprecation message");
    }

    return FramedLogMessage.generateCentered(
        List.of(
            network.normalize()
                + " is deprecated and will be shutdown "
                + network.getDeprecationDate().get(),
            "",
            "For more details please go to",
            "https://blog.ethereum.org/2022/06/21/testnet-deprecation/"));
  }
}
