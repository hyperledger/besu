package org.hyperledger.besu.consensus.qbt.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbt.support.extradata.BftExtraDataTestCaseSpec;
import org.hyperledger.besu.consensus.qbt.support.provider.JsonProvider;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BftExtraDataRlpTest {
  private static final String TEST_CONFIG_PATH = "BftExtraDataRLPTests/";
  private final BftExtraDataTestCaseSpec spec;
  private static final JsonProvider JSON_PROVIDER = new JsonProvider();

  @Parameterized.Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(
            BftExtraDataTestCaseSpec.class, JSON_PROVIDER.getObjectMapper())
        .generate(TEST_CONFIG_PATH);
  }

  public BftExtraDataRlpTest(
      final String name, final BftExtraDataTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test [" + name + "] was blacklisted", runTest);
  }

  @Test
  public void encode() {
    final BftExtraData bftExtraData = spec.getBftExtraData();
    assertThat(bftExtraData.encode()).isEqualTo(spec.getRlp());
  }

  @Test
  public void decode() {
    final BlockHeader blockHeader =
        setHeaderFieldsExceptForExtraData().extraData(spec.getRlp()).buildBlockHeader();
    final BftExtraData bftExtraData = BftExtraData.decode(blockHeader);
    final BftExtraData expectedExtraData = spec.getBftExtraData();

    assertThat(bftExtraData.getVanityData()).isEqualTo(expectedExtraData.getVanityData());
    assertThat(bftExtraData.getRound()).isEqualTo(expectedExtraData.getRound());
    assertThat(bftExtraData.getValidators())
        .containsExactlyInAnyOrder(expectedExtraData.getValidators().toArray(Address[]::new));
    assertThat(bftExtraData.getVote()).isEqualTo(bftExtraData.getVote());
    assertThat(bftExtraData.getSeals())
        .containsExactlyInAnyOrder(
            expectedExtraData.getSeals().toArray(SECP256K1.Signature[]::new));
  }

  private static BlockHeaderBuilder setHeaderFieldsExceptForExtraData() {
    final BlockHeaderBuilder builder = new BlockHeaderBuilder();
    builder.parentHash(
        Hash.fromHexString("0xa7762d3307dbf2ae6a1ae1b09cf61c7603722b2379731b6b90409cdb8c8288a0"));
    builder.ommersHash(
        Hash.fromHexString("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"));
    builder.coinbase(Address.fromHexString("0x0000000000000000000000000000000000000000"));
    builder.stateRoot(
        Hash.fromHexString("0xca07595b82f908822971b7e848398e3395e59ee52565c7ef3603df1a1fa7bc80"));
    builder.transactionsRoot(
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"));
    builder.receiptsRoot(
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"));
    builder.logsBloom(
        LogsBloomFilter.fromHexString(
            "0x000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "0000"));
    builder.difficulty(Difficulty.ONE);
    builder.number(1);
    builder.gasLimit(4704588);
    builder.gasUsed(0);
    builder.timestamp(1530674616);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.nonce(0);
    builder.blockHeaderFunctions(BftBlockHeaderFunctions.forOnChainBlock());
    return builder;
  }
}
