# Twelve Data response fixtures

Used by `TwelveDataProviderTest`. No user data, portfolio symbols or user API keys.

| File | Origin |
|---|---|
| `observed-401-demo-multi-symbol.json` | **Observed** 2026-10-17: `GET /quote?symbol=AAPL,MSFT` with `Authorization: apikey demo`. HTTP 401; the demo key does not serve multi-symbol requests. |
| `observed-401-invalid-key.json` | **Observed** 2026-10-17: `GET /quote?symbol=AAPL` with an invalid key. HTTP 401 with the same error object. |
| `synthetic-quote-single.json` | **Synthetic**, from the documented `/quote` fields (https://twelvedata.com/docs). |
| `synthetic-quote-multi.json` | **Synthetic**: a multi-symbol response keyed by symbol, with two quotes and per-symbol 403 and 404 error objects. The keyed shape is not documented for comma-separated requests and was not observed (the demo key refuses them), so the parser also accepts a single quote object. |

Observed: errors arrive with a matching HTTP status and the body `{"code": <int>, "message": "...", "status": "error"}`. The parser also accepts that body with HTTP 200, as the documentation does not rule it out.
