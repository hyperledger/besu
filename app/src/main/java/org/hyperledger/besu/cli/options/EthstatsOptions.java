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

import org.hyperledger.besu.ethstats.util.EthStatsConnectOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;

/** The Ethstats CLI options. */
public class EthstatsOptions implements CLIOptions<EthStatsConnectOptions> {

  private static final String ETHSTATS = "--ethstats";
  private static final String ETHSTATS_CONTACT = "--ethstats-contact";
  private static final String ETHSTATS_CACERT_FILE = "--ethstats-cacert-file";
  private static final String ETHSTATS_REPORT_INTERVAL = "--ethstats-report-interval";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {ETHSTATS},
      paramLabel = "<[ws://|wss://]nodename:secret@host:[port]>",
      description = "Reporting URL of a ethstats server. Scheme and port can be omitted.",
      arity = "1")
  private String ethstatsUrl = "";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {ETHSTATS_CONTACT},
      description = "Contact address to send to ethstats server",
      arity = "1")
  private String ethstatsContact = "";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {ETHSTATS_CACERT_FILE},
      paramLabel = "<FILE>",
      description =
          "Specifies the path to the root CA (Certificate Authority) certificate file that has signed ethstats server certificate. This option is optional.")
  private Path ethstatsCaCert = null;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {ETHSTATS_REPORT_INTERVAL},
      paramLabel = "<SECONDS>",
      description = "Interval in seconds between ethstats reports.",
      arity = "1")
  private Integer ethstatsReportInterval = 5;

  private EthstatsOptions() {}

  /**
   * Create ethstats options.
   *
   * @return the ethstats options
   */
  public static EthstatsOptions create() {
    return new EthstatsOptions();
  }

  @Override
  public EthStatsConnectOptions toDomainObject() {
    return EthStatsConnectOptions.fromParams(
        ethstatsUrl, ethstatsContact, ethstatsCaCert, ethstatsReportInterval);
  }

  /**
   * Gets ethstats url.
   *
   * @return the ethstats url
   */
  public String getEthstatsUrl() {
    return ethstatsUrl;
  }

  /**
   * Gets ethstats contact.
   *
   * @return the ethstats contact
   */
  public String getEthstatsContact() {
    return ethstatsContact;
  }

  /**
   * Returns path to root CA cert file.
   *
   * @return Path to CA file. null if no CA file to set.
   */
  public Path getEthstatsCaCert() {
    return ethstatsCaCert;
  }

  /**
   * Gets ethstats report interval.
   *
   * @return the ethstats report interval
   */
  public Integer getEthstatsReportInterval() {
    return ethstatsReportInterval;
  }

  @Override
  public List<String> getCLIOptions() {
    final List<String> options = new ArrayList<>();
    options.add(ETHSTATS + "=" + ethstatsUrl);
    options.add(ETHSTATS_CONTACT + "=" + ethstatsContact);
    if (ethstatsCaCert != null) {
      options.add(ETHSTATS_CACERT_FILE + "=" + ethstatsCaCert);
    }
    options.add(ETHSTATS_REPORT_INTERVAL + "=" + ethstatsReportInterval);
    return options;
  }
}
