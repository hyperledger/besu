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
package org.hyperledger.besu.consensus.merge.blockcreation;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import com.google.common.io.Resources;

public interface MergeGenesisConfigHelper {

  default GenesisConfigFile getGenesisConfigFile() {
    try {
      final URI uri = MergeGenesisConfigHelper.class.getResource("/mergeAtGenesis.json").toURI();
      return GenesisConfigFile.fromConfig(Resources.toString(uri.toURL(), UTF_8));
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  default Stream<Address> genesisAllocations() {
    return getGenesisConfigFile()
        .streamAllocations()
        .map(GenesisAllocation::getAddress)
        .map(Address::fromHexString);
  }

  default ProtocolSchedule getMergeProtocolSchedule() {
    return MergeProtocolSchedule.create(getGenesisConfigFile().getConfigOptions(), false);
  }
}
