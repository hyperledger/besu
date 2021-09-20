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
package org.hyperledger.besu.plugins;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugins.privacy.TestPrivacyGroupGenesisProvider;
import org.hyperledger.besu.plugins.privacy.TestPrivacyPluginPayloadProvider;
import org.hyperledger.besu.plugins.privacy.TestSigningPrivateMarkerTransactionFactory;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestPrivacyServicePlugin implements BesuPlugin {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void register(final BesuContext context) {
    LOG.info("Register Plugins with options " + this);

    PrivacyPluginService pluginService = context.getService(PrivacyPluginService.class).get();

    TestPrivacyPluginPayloadProvider payloadProvider = new TestPrivacyPluginPayloadProvider(prefix);
    pluginService.setPayloadProvider(payloadProvider);

    if (genesisEnabled) {
      TestPrivacyGroupGenesisProvider privacyGroupGenesisProvider =
          new TestPrivacyGroupGenesisProvider();
      pluginService.setPrivacyGroupGenesisProvider(privacyGroupGenesisProvider);
    }

    if (signingEnabled) {
      TestSigningPrivateMarkerTransactionFactory privateMarkerTransactionFactory =
          new TestSigningPrivateMarkerTransactionFactory();
      pluginService.setPrivateMarkerTransactionFactory(privateMarkerTransactionFactory);
      privateMarkerTransactionFactory.setSigningKeyEnbaled(signingKey);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Option(names = "--plugin-privacy-service-encryption-prefix")
  String prefix;

  @Option(names = "--plugin-privacy-service-genesis-enabled")
  boolean genesisEnabled = false;

  @Option(names = "--plugin-privacy-service-signing-enabled")
  boolean signingEnabled = false;

  @Option(names = "--plugin-privacy-service-signing-key")
  String signingKey;

  @Override
  public String toString() {
    return "TestPrivacyServicePlugin{"
        + "prefix='"
        + prefix
        + '\''
        + ", signingEnabled="
        + signingEnabled
        + ", genesisEnabled="
        + genesisEnabled
        + ", signingKey='"
        + signingKey
        + '\''
        + '}';
  }

  @Override
  public Optional<String> getName() {
    return Optional.of("privacy-service");
  }
}
