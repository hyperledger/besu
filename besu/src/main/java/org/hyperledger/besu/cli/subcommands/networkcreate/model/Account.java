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
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static java.util.Objects.isNull;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.GenesisFragmentable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.util.uint.UInt256;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// TODO Handle errors
class Account implements GenesisFragmentable, ConfigNode, Verifiable {

  private Address address;
  private Wei balance;
  private ConfigNode parent;

  public Account(
      @JsonProperty("address") final Address address, @JsonProperty("balance") final Wei balance) {
    this.address = address;
    this.balance = balance;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Address getAddress() {
    return address;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Wei getBalance() {
    return balance;
  }

  @JsonIgnore
  @Override
  public ObjectNode getGenesisFragment() {
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode fragment = mapper.createObjectNode();
    fragment.put("balance", balance.toString());
    return fragment;
  }

  @Override
  public ConfigNode getParent() {
    return parent;
  }

  @Override
  public void setParent(final ConfigNode parent) {
    this.parent = parent;
  }

  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(address)) {
      errorHandler.add("Account address", "null", "Account address not defined.");
    }
    if (isNull(balance)) {
      errorHandler.add("Account balance", "null", "Account balance not defined.");
    }
    if (!isNull(balance) && balance.compareTo(UInt256.ZERO) <= 0) {
      errorHandler.add(
          "Account balance", balance.toString(), "Account balance must be greater than zero.");
    }
    return errorHandler;
  }
}
