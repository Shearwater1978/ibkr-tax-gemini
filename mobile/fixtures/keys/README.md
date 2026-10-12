# Synthetic key manifest

`manifest_v1.json` is a version 1 backup key manifest (the `KeyManifest` JSON both apps store next to their backups), built with made-up inputs: the `passphrase`, `recoveryCode` and `dataKey` fields next to it. It was produced with independent libraries, argon2-cffi (the reference Argon2 implementation) and pyca/cryptography, following the Android `KeyVault`:

- passphrase KEK: Argon2id v1.3 over the UTF-8 passphrase, 64 MiB, 3 iterations, parallelism 1, 32 bytes;
- recovery KEK: HKDF-SHA256 over the ASCII recovery code, info `ibkrtax recovery kek v1`, 32 bytes;
- wraps: AES-256-GCM, 12-byte nonce + ciphertext + tag, associated data `ibkrtax.dk.v1:passphrase` or `ibkrtax.dk.v1:recovery`;
- key check: the first 16 bytes of HMAC-SHA256(data key, `ibkrtax key check v1`).

A client that unlocks it with both secrets derives keys exactly as the other platform does, so backups move between Android and iOS. Never put real keys or passphrases here.
