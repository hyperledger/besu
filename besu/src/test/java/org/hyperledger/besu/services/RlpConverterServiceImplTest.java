package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.plugin.data.BlockHeader;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class RlpConverterServiceImplTest {

  @Test
  public void testBuildRlpFromHeader() {
    // Arrange
    RlpConverterServiceImpl rlpConverterServiceImpl =
        new RlpConverterServiceImpl(ProtocolScheduleFixture.MAINNET);
    // header with cancun fields
    BlockHeader header =
        new BlockHeaderTestFixture()
            .timestamp(1710338135 + 1)
            .baseFeePerGas(Wei.of(1000))
            .depositsRoot(Hash.ZERO)
            .withdrawalsRoot(Hash.ZERO)
            .blobGasUsed(500L)
            .excessBlobGas(BlobGas.of(500L))
            .buildHeader();

    Bytes rlpBytes = rlpConverterServiceImpl.buildRlpFromHeader(header);
    BlockHeader deserialized = rlpConverterServiceImpl.buildHeaderFromRlp(rlpBytes);
    // Assert
    assertThat(header).isEqualTo(deserialized);
    assertThat(header.getBlobGasUsed()).isEqualTo(deserialized.getBlobGasUsed());
    assertThat(header.getExcessBlobGas()).isEqualTo(deserialized.getExcessBlobGas());
  }
}
