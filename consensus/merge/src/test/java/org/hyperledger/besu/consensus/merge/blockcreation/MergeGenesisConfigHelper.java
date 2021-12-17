package org.hyperledger.besu.consensus.merge.blockcreation;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import com.google.common.io.Resources;

public interface MergeGenesisConfigHelper {

  default GenesisConfigFile getGenesisConfigFile() {
    try {
      final URI uri = MergeGenesisConfigHelper.class.getResource("/mergeAtGenesis.json").toURI();
      return GenesisConfigFile.fromConfig(Resources.toString(uri.toURL(), UTF_8));
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  default Stream<Address> genesisAllocations() {
    return getGenesisConfigFile()
        .streamAllocations()
        .map(GenesisAllocation::getAddress)
        .map(Address::fromHexString);
  }

  default ProtocolSchedule getMergeProtocolSchedule() {
    return MergeProtocolSchedule.create(getGenesisConfigFile().getConfigOptions(), false);
  }
}
