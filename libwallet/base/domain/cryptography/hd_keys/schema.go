package hd_keys

// SchemaBasePath is the derivation path every wallet key derives from: the
// base keys sit there and address paths hang below it.
const SchemaBasePath = "m/schema:1'/recovery:1'"

// SecurityCardClientApprovalKeyPath is the key the client signs security card
// challenge approvals with. It is a non-hardened child of the base key, so
// Houston derives its public half from the user's base public key, which it
// already holds, without the client registering it. Its branch, cards:5,
// holds no addresses.
const SecurityCardClientApprovalKeyPath = SchemaBasePath + "/cards:5/0"
