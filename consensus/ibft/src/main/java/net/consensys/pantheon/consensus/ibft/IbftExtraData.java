package net.consensys.pantheon.consensus.ibft;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPInput;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an IBFT consensus mechanism.
 */
public class IbftExtraData {

  public static final int EXTRA_VANITY_LENGTH = 32;

  private final BytesValue vanityData;
  private final List<Signature> seals;
  private final Signature proposerSeal;
  private final List<Address> validators;

  public IbftExtraData(
      final BytesValue vanityData,
      final List<Signature> seals,
      final Signature proposerSeal,
      final List<Address> validators) {

    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);

    this.vanityData = vanityData;
    this.seals = seals;
    this.proposerSeal = proposerSeal;
    this.validators = validators;
  }

  public static IbftExtraData decode(final BytesValue input) {
    checkArgument(
        input.size() > EXTRA_VANITY_LENGTH,
        "Invalid BytesValue supplied - too short to produce a valid IBFT Extra Data object.");

    final BytesValue vanityData = input.slice(0, EXTRA_VANITY_LENGTH);

    final BytesValue rlpData = input.slice(EXTRA_VANITY_LENGTH);
    final RLPInput rlpInput = new BytesValueRLPInput(rlpData, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains IBFT data items.
    final List<Address> validators = rlpInput.readList(Address::readFrom);
    final Signature proposerSeal = parseProposerSeal(rlpInput);
    final List<Signature> seals = rlpInput.readList(rlp -> Signature.decode(rlp.readBytesValue()));
    rlpInput.leaveList();

    return new IbftExtraData(vanityData, seals, proposerSeal, validators);
  }

  private static Signature parseProposerSeal(final RLPInput rlpInput) {
    final BytesValue data = rlpInput.readBytesValue();
    return data.isZero() ? null : Signature.decode(data);
  }

  public BytesValue encode() {
    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));
    if (proposerSeal != null) {
      encoder.writeBytesValue(proposerSeal.encodedBytes());
    } else {
      encoder.writeNull();
    }
    encoder.writeList(seals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    return BytesValue.wrap(vanityData, encoder.encoded());
  }

  // Accessors
  public BytesValue getVanityData() {
    return vanityData;
  }

  public List<Signature> getSeals() {
    return seals;
  }

  public Signature getProposerSeal() {
    return proposerSeal;
  }

  public List<Address> getValidators() {
    return validators;
  }
}
