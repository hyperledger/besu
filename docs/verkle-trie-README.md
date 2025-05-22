# Verkle Trie Documentation

This directory contains documentation for the Verkle trie implementation in Hyperledger Besu.

## What are Verkle Tries?

Verkle tries are an advancement over the traditional Merkle Patricia Tries (MPTs) currently used in Ethereum. They provide more efficient proofs and improved scalability, which is crucial for Ethereum's future growth.

The name "Verkle" combines "Vector commitment" and "Merkle". Unlike Merkle trees where proof size grows linearly with tree depth, Verkle tries have nearly constant-sized proofs regardless of the trie's depth.

## Documentation Index

1. [Verkle Trie Implementation](./verkle-trie-implementation.md) - Technical overview of how Verkle tries are implemented in Besu
2. [Verkle Trie Development Guide](./verkle-trie-development-guide.md) - Guide for developers working with or contributing to the Verkle trie code

## Key Benefits of Verkle Tries

1. **Smaller Proof Sizes**: Verkle tries significantly reduce the size of state proofs
2. **Improved Scalability**: Support for stateless clients and reduced state size
3. **Enhanced Performance**: More efficient state access and updates
4. **Future-Proofing**: Part of Ethereum's roadmap for scaling and efficiency improvements

## Implementation Status

The Verkle trie implementation in Besu is currently in development. The codebase includes:

- Core cryptographic primitives (elliptic curve operations, finite field arithmetic)
- Bandersnatch curve implementation
- Basic point and field operations

The full trie structure, including proof generation and verification, is under active development.

## Related Resources

- [EIP-6800: Verkle Trees](https://eips.ethereum.org/EIPS/eip-6800)
- [Ethereum Research - Verkle Tries](https://notes.ethereum.org/@vbuterin/verkle_tree_eip)
- [Besu Verkle Trie Repository](https://github.com/hyperledger/besu-verkle-trie)

## Contributing

We welcome contributions to both the codebase and documentation. Please see the [Development Guide](./verkle-trie-development-guide.md) for information on how to contribute.

## Feedback

If you have any questions or feedback about this documentation or the Verkle trie implementation, please open an issue on the [Besu GitHub repository](https://github.com/hyperledger/besu/issues). 
