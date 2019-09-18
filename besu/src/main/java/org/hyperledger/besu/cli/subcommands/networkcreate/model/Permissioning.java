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
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.checkerframework.checker.nullness.qual.Nullable;

// TODO Handle errors
class Permissioning {
  private Boolean deployDapp;
  private Boolean allNodesAdmin;
  private Boolean whitelistAccounts;

  public Permissioning(
      @Nullable @JsonProperty("deploy-dapp") final Boolean deployDapp,
      @Nullable @JsonProperty("all-nodes-admin") final Boolean allNodesAdmin,
      @Nullable @JsonProperty("whitelist-accounts") final Boolean whitelistAccounts) {
    this.deployDapp = requireNonNullElse(deployDapp, false);
    this.allNodesAdmin = requireNonNullElse(allNodesAdmin, false);
    this.whitelistAccounts = requireNonNullElse(whitelistAccounts, false);
  }

  public Boolean isDeployDapp() {
    return deployDapp;
  }

  public Boolean isMakeAllNodesAdmin() {
    return allNodesAdmin;
  }

  public Boolean isWhitelistAccounts() {
    return whitelistAccounts;
  }
}
