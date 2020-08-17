package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethstats.util.NetstatsUrl;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class EthstatsOptions implements CLIOptions<NetstatsUrl> {

  private static final String ETHSTATS = "--Xethstats";
  private static final String ETHSTATS_CONTACT = "--Xethstats-contact";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      hidden = true,
      names = {ETHSTATS},
      paramLabel = "<nodename:secret@host:port>",
      description = "Reporting URL of a ethstats server",
      arity = "1")
  private String ethstatsUrl = "";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      hidden = true,
      names = {ETHSTATS_CONTACT},
      description = "Contact address to send to ethstats server",
      arity = "1")
  private String ethstatsContact = "";

  private EthstatsOptions() {}

  public static EthstatsOptions create() {
    return new EthstatsOptions();
  }

  @Override
  public NetstatsUrl toDomainObject() {
    return NetstatsUrl.fromParams(ethstatsUrl, ethstatsContact);
  }

  public String getEthstatsUrl() {
    return ethstatsUrl;
  }

  public String getEthstatsContact() {
    return ethstatsContact;
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(ETHSTATS + "=" + ethstatsUrl, ETHSTATS_CONTACT + "=" + ethstatsContact);
  }
}
