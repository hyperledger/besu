package tech.pegasys.pantheon.consensus.ibft.protocol;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

import java.util.List;

import com.google.common.collect.Lists;

/** This allows for interoperability with Quorum, but shouldn't be used otherwise. */
public class Istanbul64ProtocolManager extends EthProtocolManager {

  public Istanbul64ProtocolManager(
      final Blockchain blockchain,
      final int networkId,
      final boolean fastSyncEnabled,
      final int workers) {
    super(blockchain, networkId, fastSyncEnabled, workers);
  }

  @Override
  public void processMessage(final Capability cap, final Message message) {
    if (cap.equals(Istanbul64Protocol.ISTANBUL64)) {
      if (message.getData().getCode() != Istanbul64Protocol.INSTANBUL_MSG) {
        super.processMessage(EthProtocol.ETH63, message);
      } else {
        // TODO(tmm): Determine if the message should be routed to ibftController at a later date.
      }
    }
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    final List<Capability> result = Lists.newArrayList(Istanbul64Protocol.ISTANBUL64);
    result.addAll(super.getSupportedCapabilities());
    return result;
  }

  @Override
  public String getSupportedProtocol() {
    return Istanbul64Protocol.get().getName();
  }
}
