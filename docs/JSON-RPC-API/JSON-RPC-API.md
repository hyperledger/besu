description: Pantheon JSON-RPC API reference
<!--- END of page meta data -->

# JSON-RPC API Overview

The Pantheon JSON-RPC API uses the [JSON-RPC v2.0](https://www.jsonrpc.org/specification) specification of the JSON-RPC protocol. 
  
The [JSON](http://json.org/) (RFC 4627) format which represents 
objects and data fields as collections of name/value pairs, in a readable, hierarchical form. 
Values have specific data types such as quantities (decimal integers, hexadecimal numbers, strings) and 
unformatted data (byte arrays, account addresses, hashes, and bytecode arrays).

RPC is the remote procedure call protocol (RFC 1831). The protocol is stateless and transport agnostic in that the concepts 
can be used within the same process, over sockets, over HTTP, or in various message passing environments.

The Reference documentation includes the [JSON-RPC API Methods](../Reference/JSON-RPC-API-Methods.md)
and [JSON-RPC API Objects](../Reference/JSON-RPC-API-Objects.md)