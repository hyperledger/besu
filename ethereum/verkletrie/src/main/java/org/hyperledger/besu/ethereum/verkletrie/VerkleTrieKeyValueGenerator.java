package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.IPAHasher;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleTrieKeyValueGenerator {

  private static final int PUSH_OFFSET = 95;
  private static final int PUSH1 = PUSH_OFFSET + 1;
  private static final int PUSH32 = PUSH_OFFSET + 32;

  final TrieKeyAdapter trieKeyAdapter = new TrieKeyAdapter(new IPAHasher()); // TODO Change

  public Map<Bytes, Bytes> generateKeyValuesForAccount(
      final Bytes32 address32, final long nonce, final Wei balance) {
    final Map<Bytes, Bytes> keyValues = new HashMap<>();
    keyValues.put(trieKeyAdapter.versionKey(address32), Bytes32.ZERO);
    keyValues.put(trieKeyAdapter.balanceKey(address32), toLittleIndian(balance));
    keyValues.put(trieKeyAdapter.nonceKey(address32), toLittleIndian(UInt256.valueOf(nonce)));
    return keyValues;
  }

  public List<Bytes> generateKeysForAccount(final Bytes32 address32) {
    final List<Bytes> keys = new ArrayList<>();
    keys.add(trieKeyAdapter.versionKey(address32));
    keys.add(trieKeyAdapter.balanceKey(address32));
    keys.add(trieKeyAdapter.nonceKey(address32));
    return keys;
  }

  public Map<Bytes, Bytes> generateKeyValuesForCode(
      final Bytes32 address32, final Bytes32 keccakCodeHash, final Bytes code) {
    final Map<Bytes, Bytes> keyValues = new HashMap<>();
    keyValues.put(trieKeyAdapter.codeKeccakKey(address32), keccakCodeHash);
    keyValues.put(
        trieKeyAdapter.codeSizeKey(address32), toLittleIndian(UInt256.valueOf(code.size())));
    List<Bytes32> codeChunks = chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      keyValues.put(trieKeyAdapter.codeChunkKey(address32, UInt256.valueOf(i)), codeChunks.get(i));
    }
    return keyValues;
  }

  public List<Bytes> generateKeysForCode(final Bytes32 address32, final Bytes code) {
    final List<Bytes> keys = new ArrayList<>();
    keys.add(trieKeyAdapter.codeKeccakKey(address32));
    keys.add(trieKeyAdapter.codeSizeKey(address32));
    List<Bytes32> codeChunks = chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      keys.add(trieKeyAdapter.codeChunkKey(address32, UInt256.valueOf(i)));
    }
    return keys;
  }

  public Pair<Bytes, Bytes> generateKeyValuesForStorage(
      final Bytes32 address32, final StorageSlotKey storageKey, final Bytes value) {
    return new Pair<>(
        trieKeyAdapter.storageKey(address32, storageKey.getSlotKey().orElseThrow()), value);
  }

  public List<Bytes> generateKeysForStorage(
      final Bytes32 address32, final StorageSlotKey storageKey) {
    return List.of(trieKeyAdapter.storageKey(address32, storageKey.getSlotKey().orElseThrow()));
  }

  private List<Bytes32> chunkifyCode(final Bytes code) {
    // Pad to multiple of 31 bytes
    final int modulo = code.size() % 31;
    final Bytes codeChunk;
    if (modulo != 0) {
      codeChunk = Bytes.concatenate(code, Bytes.repeat((byte) 0, 31 - modulo));
    } else {
      codeChunk = code;
    }

    // Figure out how much pushdata there is after+including each byte
    MutableBytes bytesToExecData = MutableBytes.create(codeChunk.size() + Bytes32.SIZE);
    bytesToExecData.fill((byte) 0x00);

    int pos = 0;
    while (pos < codeChunk.size()) {
      int pushdataBytes;
      if (PUSH1 <= (codeChunk.get(pos) & 0xFF) && (codeChunk.get(pos) & 0xFF) <= PUSH32) {
        pushdataBytes = (codeChunk.get(pos) & 0xFF) - PUSH_OFFSET;
      } else {
        pushdataBytes = 0;
      }
      pos += 1;
      for (int x = 0; x < pushdataBytes; x++) {
        bytesToExecData.set(pos + x, Bytes.of(pushdataBytes - x));
      }
      pos += pushdataBytes;
    }

    // Output chunks
    final List<Bytes32> chunks = new ArrayList<>();
    for (pos = 0; pos < codeChunk.size(); pos += 31) {
      ByteBuffer buffer = ByteBuffer.allocate(32);
      buffer.put((byte) Math.min(bytesToExecData.get(pos), 31));
      buffer.put(codeChunk.slice(pos, Math.min(31, codeChunk.size() - pos)).toArray());
      buffer.flip();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      chunks.add(Bytes32.wrap(bytes));
    }
    return chunks;
  }

  public static Bytes32 rightPad(final Bytes originalValue) {
    final MutableBytes32 padded = MutableBytes32.create();
    padded.set(0, originalValue);
    return padded;
  }

  public static Bytes toLittleIndian(final Bytes originalValue) {
    return originalValue.reverse();
  }
}
