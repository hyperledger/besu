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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.plugin.data.PluginSubProtocol;

/**
 * Adapter class that converts a PluginSubProtocol to a SubProtocol.
 */
public class SubProtocolAdapter implements SubProtocol {

  private final PluginSubProtocol pluginSubProtocol;

  private SubProtocolAdapter(final PluginSubProtocol pluginSubProtocol) {
    this.pluginSubProtocol = pluginSubProtocol;
  }

  public static SubProtocol create(final PluginSubProtocol pluginSubProtocol) {
    return new SubProtocolAdapter(pluginSubProtocol);
  }

  @Override
  public String getName() {
    return pluginSubProtocol.getName();
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return pluginSubProtocol.messageSpace(protocolVersion);
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    return pluginSubProtocol.isValidMessageCode(protocolVersion, code);
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    return pluginSubProtocol.messageName(protocolVersion, code);
  }
}
