## Privacy

### Private Transaction

A transaction conforming to the definition of a *restricted* transaction
as defined in the EEA Client Specification v2. The private payload is
only stored within the nodes participating in the transaction and not
in any other node. Private transactions are kept private between related
parties, so unrelated parties have no access to the content of the 
transaction, the sending party, or the list of participating addresses.
The sending actor is specified in the `privateFrom` attribute of the 
transaction. The receiving parties are specified in the `privateFor` 
attribute of the transaction. See [eea_sendrawtransaction](../Reference/JSON-RPC-API-Methods.md#eea_sendRawTransaction)
for implementation details.

### Important Concepts
- **Precompiled Contract**: A smart contract compiled from its source
 language to EVM bytecode and stored by an Ethereum node for later
 execution.
- **Privacy**: It is “The right of individuals to control or influence
 what information related to them may be collected and stored and by
 whom and to whom that information may be disclosed.” For the purposes
 of this Specification, this right of privacy also applies to
 organizations to the extent permitted by law.
- **Private State**: Data that is not shared in the clear in the
 globally replicated state tree. This data can represent bilateral or
 multilateral arrangements between counterparties, for example in
 private transactions.
- **Private Transaction**: A transaction where some information about
 the transaction, such as the payload data, or the sender and
 recipient, is only available to the subset of network actors who are
 parties to that transaction.
- **Private Transaction Manager**: A subsystem of an Enterprise Ethereum
  system for implementing privacy known as an Enclave.
  e.g. [Orion](https://github.com/PegaSysEng/orion).
- **Actor**: The public key of someone involved in the private transaction (e.g. a `privateFor` actor).
- **Privacy group id**: A unique identifier associated with the set of actors
 that are involved in private transaction.


### How Private Transaction is Processed

1. A private transaction is sent to the [eea_sendrawtransaction](../Reference/JSON-RPC-API-Methods.md#eea_sendRawTransaction)
endpoint, using the `privateFor` attribute to specify the list of actors,
The `privateFrom` attribute specifies the sending actor, and `restriction`
attribute is set to *restricted*.
 
2. The JSON RPC endpoint hands off the private transaction to a
Private Transaction Handler.

3. The Private Transaction Handler sends the private transaction to an Enclave 
(Private Transaction Manager)

4. The Enclave will store the private transaction in map. The transaction will
be associated with a random key (Enclave-key) identifying that transaction 
in the enclave and a privacy group id.

5. The Enclave distributes the private transaction point-to-point directly
to the enclaves of actors specified in the `privateFor` attribute. All
enclaves storing this transaction will associate it with the same key and 
privacy group id.

6. The Enclave returns the Enclave-key back to the Private
Transaction Handler (as a response to the synchronous HTTP call).
     
7. The Private Transaction Handler will create a Privacy Marker Transaction (PMT).
A PMT is a standard public Ethereum transaction. The payload of the PMT 
is the Enclave-key of the private transaction. The `to` attribute of the PMT the 
privacy precompile contract. The PMT is signed with the nodes default key.
The PMT is sent to the transaction pool. Once the PMT has been mined in a block
it is distributed to all nodes in the network. 

8. The Transaction Processor of every node in the network will process the 
PMT as per any other public transaction. The PMT will be passed to the
privacy precompile contract on those nodes that contain that precompile
contract specified in the `to` attribute of the PMT.

8. The Privacy Precompile Contract queries the Enclave for the Private
Transaction and Privacy Group Id based on the Enclave-key.

9. The Privacy Precompile Contract executes the Private Transaction using the
Private Transaction Processor, specifying the private world state to use based on the
Privacy Group Id. The Private Transaction Processor can read and write to 
the private world state, and read from the public world state.

### Privacy JSON-RPC API method

The [EEA methods](../Reference/JSON-RPC-API-Methods.md#eea_sendRawTransaction) created to
provide and support privacy.

!!!note EEA methods are for privacy features. Privacy features are under development and will be available in v1.1.

