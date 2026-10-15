# Sui upstream provenance

This directory is FinaSU's built-in copy of Sui, the Shizuku API implementation
that runs as a Zygisk module. Upstream source, copyright headers and license
texts are retained.

The backend is Sui upstream; the frontend and the wiring are independently
maintained by FinaSU.

## Pinned sources

- Sui: https://github.com/XiaoTong6666/Sui
  commit `e9674238a0dcfe13d9ea231cbdf866144e660db8` (v13.5.4.3).
- Shizuku-API (Sui's `api` submodule): https://github.com/XiaoTong6666/Shizuku-API
  commit `13aeedbc9d83e65b3a4c74b736ca54c768dcb819` (api 13.1.5).

## Acknowledgements

Thanks to the Sui Contributors and RikkaW for Sui and Shizuku-API. Original
authors are the Sui Contributors and RikkaW (Shizuku-API). Individual source
headers carry the upstream notices; no copyright notice has been replaced with
FinaSU attribution.

## Retained license terms

Sui is GPL-3.0; Shizuku-API is MIT. Both `LICENSE` files are preserved verbatim.
FinaSU is GPL-3.0, so the combined work is redistributable.