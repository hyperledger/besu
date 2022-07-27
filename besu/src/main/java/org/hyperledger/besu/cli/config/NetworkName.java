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
package org.hyperledger.besu.cli.config;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public enum NetworkName {
  MAINNET("/mainnet.json", BigInteger.valueOf(1)),
  RINKEBY("/rinkeby.json", BigInteger.valueOf(4)),
  ROPSTEN("/ropsten.json", BigInteger.valueOf(3)),
  SEPOLIA("/sepolia.json", BigInteger.valueOf(11155111)),
  GOERLI("/goerli.json", BigInteger.valueOf(5)),
  KILN("/kiln.json", BigInteger.valueOf(1337802), false),
  DEV("/dev.json", BigInteger.valueOf(2018), false),
  CLASSIC("/classic.json", BigInteger.valueOf(1)),
  KOTTI("/kotti.json", BigInteger.valueOf(6)),
  MORDOR("/mordor.json", BigInteger.valueOf(7)),
  ECIP1049_DEV("/ecip1049_dev.json", BigInteger.valueOf(2021)),
  ASTOR("/astor.json", BigInteger.valueOf(212));

  private final String genesisFile;
  private final BigInteger networkId;
  private final boolean canFastSync;
  private final String deprecationDate;

  NetworkName(final String genesisFile, final BigInteger networkId) {
    this(genesisFile, networkId, true);
  }

  NetworkName(final String genesisFile, final BigInteger networkId, final boolean canFastSync) {
    this.genesisFile = genesisFile;
    this.networkId = networkId;
    this.canFastSync = canFastSync;

    // https://blog.ethereum.org/2022/06/21/testnet-deprecation/
    switch (networkId.intValue()) {
      case 3:
        deprecationDate = "in Q4 2022";
        break;
      case 4:
        deprecationDate = "in Q2/Q3 2023";
        break;
      case 1337802:
        deprecationDate = "after the Mainnet Merge";
        break;
      default:
        deprecationDate = null;
    }
  }

  public String getGenesisFile() {
    return genesisFile;
  }

  public BigInteger getNetworkId() {
    return networkId;
  }

  public boolean canFastSync() {
    return canFastSync;
  }

  public String humanReadableNetworkName() {
    return StringUtils.capitalize(name().toLowerCase());
  }

  public boolean isDeprecated() {
    return deprecationDate != null;
  }

  public Optional<String> getDeprecationDate() {
    return Optional.ofNullable(deprecationDate);
  }
}
