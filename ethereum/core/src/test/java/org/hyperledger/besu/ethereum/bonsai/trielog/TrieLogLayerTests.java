package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class TrieLogLayerTests {
  private TrieLogLayer trieLogLayer;
  private TrieLogLayer otherTrieLogLayer;

  @Before
  public void setUp() {
    trieLogLayer = new TrieLogLayer();
    otherTrieLogLayer = new TrieLogLayer();
  }

  @Test
  public void testAddAccountChange() {
    Address address = Address.fromHexString("0x00");
    StateTrieAccountValue oldValue = new StateTrieAccountValue(0, Wei.ZERO, Hash.EMPTY, Hash.EMPTY);
    StateTrieAccountValue newValue =
        new StateTrieAccountValue(1, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY);

    Address otherAddress = Address.fromHexString("0x000000");
    StateTrieAccountValue otherOldValue =
        new StateTrieAccountValue(0, Wei.ZERO, Hash.EMPTY, Hash.EMPTY);
    StateTrieAccountValue otherNewValue =
        new StateTrieAccountValue(1, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY);

    trieLogLayer.addAccountChange(address, oldValue, newValue);
    otherTrieLogLayer.addAccountChange(otherAddress, otherOldValue, otherNewValue);

    Assertions.assertThat(trieLogLayer).isEqualTo(otherTrieLogLayer);

    Optional<StateTrieAccountValue> priorAccount = trieLogLayer.getPriorAccount(address);
    Assertions.assertThat(priorAccount).isPresent();
    Assertions.assertThat(priorAccount.get()).isEqualTo(oldValue);

    Optional<StateTrieAccountValue> updatedAccount = trieLogLayer.getAccount(address);
    Assertions.assertThat(updatedAccount).isPresent();
    Assertions.assertThat(updatedAccount.get()).isEqualTo(newValue);
  }

  @Test
  public void testAddCodeChange() {
    Address address = Address.fromHexString("0xdeadbeef");
    Bytes oldValue = Bytes.fromHexString("0x00");
    Bytes newValue = Bytes.fromHexString("0x01030307");
    Hash blockHash = Hash.fromHexStringLenient("0xfeedbeef02dead");

    Address otherAddress = Address.fromHexString("0x0000deadbeef");
    Bytes otherOldValue = Bytes.fromHexString("0x0000");
    Bytes otherNewValue = Bytes.fromHexString("0x0001030307");
    Hash otherBlockHash = Hash.fromHexStringLenient("0x00feedbeef02dead");

    trieLogLayer.addCodeChange(address, oldValue, newValue, blockHash);
    otherTrieLogLayer.addCodeChange(otherAddress, otherOldValue, otherNewValue, otherBlockHash);

    Assertions.assertThat(trieLogLayer).isEqualTo(otherTrieLogLayer);

    Optional<Bytes> priorCode = trieLogLayer.getPriorCode(address);
    Assertions.assertThat(priorCode).isPresent();
    Assertions.assertThat(priorCode.get()).isEqualTo(oldValue);

    Optional<Bytes> updatedCode = trieLogLayer.getCode(address);
    Assertions.assertThat(updatedCode).isPresent();
    Assertions.assertThat(updatedCode.get()).isEqualTo(newValue);
  }

  @Test
  public void testAddStorageChange() {
    Address address = Address.fromHexString("0x0");
    UInt256 oldValue = UInt256.ZERO;
    UInt256 newValue = UInt256.ONE;
    UInt256 slot = UInt256.ONE;

    Address otherAddress = Address.fromHexString("0x0");
    UInt256 otherOldValue = UInt256.ZERO;
    UInt256 otherNewValue = UInt256.ONE;
    UInt256 otherSlot = UInt256.ONE;

    trieLogLayer.addStorageChange(address, Hash.hash(slot), oldValue, newValue);
    otherTrieLogLayer.addStorageChange(
        otherAddress, Hash.hash(otherSlot), otherOldValue, otherNewValue);

    Assertions.assertThat(trieLogLayer).isEqualTo(otherTrieLogLayer);

    Optional<UInt256> priorStorageValue =
        trieLogLayer.getPriorStorageBySlotHash(address, Hash.hash(slot));
    Assertions.assertThat(priorStorageValue).isPresent();
    Assertions.assertThat(priorStorageValue.get()).isEqualTo(oldValue);

    Optional<UInt256> updatedStorageValue =
        trieLogLayer.getStorageBySlotHash(address, Hash.hash(slot));
    Assertions.assertThat(updatedStorageValue).isPresent();
    Assertions.assertThat(updatedStorageValue.get()).isEqualTo(newValue);
  }
}
