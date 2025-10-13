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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

/** The Dns CLI options. */
public class DnsOptions implements CLIOptions<EnodeDnsConfiguration> {

  private final String DNS_ENABLED = "--Xdns-enabled";
  private final String DNS_UPDATE_ENABLED = "--Xdns-update-enabled";

  @CommandLine.Option(
      hidden = true,
      names = {"--Xdns-enabled"},
      description = "Enabled DNS support",
      arity = "1")
  private Boolean dnsEnabled = Boolean.FALSE;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xdns-update-enabled"},
      description = "Allow to detect an IP update automatically",
      arity = "1")
  private Boolean dnsUpdateEnabled = Boolean.FALSE;

  /** Instantiates a new Dns options. */
  DnsOptions() {}

  /**
   * Create dns options.
   *
   * @return the dns options
   */
  public static DnsOptions create() {
    return new DnsOptions();
  }

  /**
   * From config dns options.
   *
   * @param enodeDnsConfiguration the enode dns configuration
   * @return the dns options
   */
  public static DnsOptions fromConfig(final EnodeDnsConfiguration enodeDnsConfiguration) {
    final DnsOptions cliOptions = new DnsOptions();
    cliOptions.dnsEnabled = enodeDnsConfiguration.dnsEnabled();
    cliOptions.dnsUpdateEnabled = enodeDnsConfiguration.updateEnabled();
    return cliOptions;
  }

  /**
   * Gets dns enabled.
   *
   * @return the dns enabled
   */
  public Boolean getDnsEnabled() {
    return dnsEnabled;
  }

  /**
   * Gets dns update enabled.
   *
   * @return the dns update enabled
   */
  public Boolean getDnsUpdateEnabled() {
    return dnsUpdateEnabled;
  }

  @Override
  public EnodeDnsConfiguration toDomainObject() {
    return ImmutableEnodeDnsConfiguration.builder()
        .updateEnabled(dnsUpdateEnabled)
        .dnsEnabled(dnsEnabled)
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        DNS_ENABLED, dnsEnabled.toString(), DNS_UPDATE_ENABLED, dnsUpdateEnabled.toString());
  }
}
