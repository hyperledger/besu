package tech.pegasys.pantheon.ethereum.eth.manager;

public class EthContext {

  private final String protocolName;
  private final EthPeers ethPeers;
  private final EthMessages ethMessages;
  private final EthScheduler scheduler;

  public EthContext(
      final String protocolName,
      final EthPeers ethPeers,
      final EthMessages ethMessages,
      final EthScheduler scheduler) {
    this.protocolName = protocolName;
    this.ethPeers = ethPeers;
    this.ethMessages = ethMessages;
    this.scheduler = scheduler;
  }

  public String getProtocolName() {
    return protocolName;
  }

  public EthPeers getEthPeers() {
    return ethPeers;
  }

  public EthMessages getEthMessages() {
    return ethMessages;
  }

  public EthScheduler getScheduler() {
    return scheduler;
  }
}
