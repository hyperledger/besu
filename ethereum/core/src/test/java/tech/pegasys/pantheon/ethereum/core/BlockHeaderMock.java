package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.vm.TestBlockchain;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A memory mock for testing. */
@JsonIgnoreProperties("previousHash")
public class BlockHeaderMock extends BlockHeader {

  /**
   * Public constructor.
   *
   * @param coinbase The beneficiary address.
   * @param gasLimit The gas limit of the current block.
   * @param number The number to execute.
   */
  @JsonCreator
  public BlockHeaderMock(
      @JsonProperty("currentCoinbase") final String coinbase,
      @JsonProperty("currentDifficulty") final String difficulty,
      @JsonProperty("currentGasLimit") final String gasLimit,
      @JsonProperty("currentNumber") final String number,
      @JsonProperty("currentTimestamp") final String timestamp) {
    super(
        TestBlockchain.generateTestBlockHash(Long.decode(number) - 1),
        Hash.EMPTY, // ommersHash
        Address.fromHexString(coinbase),
        Hash.EMPTY, // stateRoot
        Hash.EMPTY, // transactionsRoot
        Hash.EMPTY, // receiptsRoot
        new LogsBloomFilter(),
        UInt256.fromHexString(difficulty),
        Long.decode(number),
        Long.decode(gasLimit),
        0L,
        Long.decode(timestamp),
        BytesValue.EMPTY,
        Hash.ZERO,
        0L,
        MainnetBlockHashFunction::createHash);
  }
}
