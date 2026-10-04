# Historical evidence — never upload these bundles

These files were moved unchanged from tracked `app/release/app-release.aab`
and `app/debug/app-debug.aab`. The date in their names refers to the historical
certificate/source era, not a verified build date. Neither contains this release
work. A historical signature does not establish acceptance by Google Play.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| NOT-FOR-UPLOAD-2023-release.aab | 53216262 | e93bb736c81b1428bbb5a5843277a4034602b63e0e351037cccf4b336fde1639 |
| NOT-FOR-UPLOAD-2023-debug.aab | 55495390 | 19599895bc1af938b726d831a7c1dd023c08d669ecdf7b99ccdc2c69ae4900f6 |

The historical release certificate SHA-256 is
`74:05:5D:31:23:AF:73:04:C8:CA:FB:D9:09:A3:C4:DA:E3:90:C9:CA:CB:41:6D:53:6B:8A:6F:7F:A6:2B:77:40`.
The debug-named bundle has no JAR signature. No matching release private key
has been located and no Play certificate evidence has been supplied.

Current build outputs belong only in `app/build/outputs/`. The authoritative
future upload input is `app/build/outputs/bundle/release/app-release.aab`, for the new `rew.lightgames.zensudoku` listing after fresh upload signing
and exact-binary validation. Old-identity signing continuity is irrelevant to
this new listing; consult `../UPLOAD-SIGNING.md`. No file in this directory is a candidate.
