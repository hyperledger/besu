/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeWhitelistController {

  private static final Logger LOG = LogManager.getLogger();

  private static List<String> nodeWhitelist;
  private static boolean nodeWhitelistSet = false;

  public NodeWhitelistController(final PermissioningConfiguration configuration) {
    nodeWhitelist = new ArrayList<>();
    if (configuration != null && configuration.getNodeWhitelist() != null) {
      nodeWhitelist.addAll(configuration.getNodeWhitelist());
      nodeWhitelistSet = true;
    }
  }

  public boolean addNode(final String nodeId) {
    return nodeWhitelist.add(nodeId);
  }

  public boolean removeNode(final String nodeId) {
    return nodeWhitelist.remove(nodeId);
  }

  public static boolean isNodeWhitelistSet() {
    return nodeWhitelistSet;
  }
}
