# glyph

glyph is a text-to-image render service you build from scratch. Internal teams hand it some text and a font file, and it draws that text in that font and hands back an image. Picture the path the characters travel: the font file holds the shape of every glyph along with how much space each one takes up, your code reads those shapes, lays the characters out left to right so they don't pile on top of each other, paints them onto a canvas, and encodes the canvas as an image you can open and look at.

## Your task

You have two jobs.

1. **Build the core and get it working.** Read a font file, take an input string, and lay the glyphs out one after another using each glyph's advance width so the spacing looks right. There are a few TTFs in the `fonts/` folder to render, or bring your own. Rasterize the glyphs onto a canvas and write the result out as an image. The output format is your call — PNG, PPM, even a plain bitmap is fine, as long as it opens and shows the text. You're welcome to use a library to *parse* the font and pull out glyph outlines and metrics, but turning those outlines into pixels and placing the glyphs is the exercise — a one-call text renderer, or a library that hands you finished glyph bitmaps, is off-limits. You don't need to parse the font's binary tables by hand either; that part's fine to delegate.
2. **Make it a service.** Once it renders, internal teams are going to call it over HTTP, a lot, and lean on it. Take it as far toward something you'd be comfortable putting in front of them as you can. What that takes is yours to figure out. You won't get to everything in the time you have, and we don't expect you to. Be ready to walk through what you tackled first, what you left out, and why.
  One thing that's *not* in scope is deployment. We're not hosting this anywhere, so skip Dockerfiles, CI, and infra. The process running on your laptop is the service.

We care more about your judgment on what to tackle first than about a long list of half-finished features.


## Running the core

Build and test:

```
mvn -q test
mvn -q package -DskipTests
```

Render text to a PNG (optional last arg is size in px, 4-512, default 64):

```
java -jar target/glyph.jar fonts/DejaVuSans.ttf "Hello, glyph!" target/out/cli.png 48
```

Exit codes: 0 success, 1 error, 2 bad usage.

## Running the service

```
mvn -q package -DskipTests
java -jar target/glyph.jar serve
```

Listens on `127.0.0.1:8080` by default (no auth yet, so loopback only; set `HOST=0.0.0.0` to expose it).
All config is environment variables:

| Var | Default | Meaning |
|-----|---------|---------|
| `HOST` / `PORT` | `127.0.0.1` / `8080` | Bind address |
| `FONT_DIR` | `fonts` | Every `*.ttf` here is loaded at startup and addressed by file name without `.ttf`. Bad font or empty dir = startup fails. |
| `MAX_CONCURRENT_RENDERS` | CPU cores | Renders running at once |
| `QUEUE_WAIT_MS` | `500` | How long a request waits for a render slot before getting `503` + `Retry-After: 1` |
| `CACHE_MB` | `64` | LRU of encoded PNGs (`0` disables) |
| `MAX_PIXELS` | `4000000` | Largest canvas a request may produce |

### API

`GET /v1/render?font=DejaVuSans&text=Hello%2C+glyph%21` returns `image/png`.

| Param | Required | Default | Rules |
|-------|----------|---------|-------|
| `font` | yes | | A name from `/v1/fonts`. Never a path. |
| `text` | yes | | UTF-8, percent-encoded, 1-1000 code points, single line |
| `size` | no | `64` | integer px per em, 4-512 |
| `padding` | no | `8` | integer px, 0-256 |
| `ink`, `bg` | no | `000000`, `ffffff` | `RRGGBB` or `RRGGBBAA`, no `#` (`bg=00000000` is transparent) |

Unknown or repeated params are rejected, so typos fail loudly. Responses carry `ETag` and
`Cache-Control: public, max-age=3600`; send `If-None-Match` to get a body-less `304`. Output is
deterministic, so results are safe to cache downstream.

Other routes: `GET /v1/fonts`, `GET /healthz` (liveness), `GET /readyz` (readiness; `503` while draining),
`GET /metrics` (Prometheus text).

Errors are JSON: `{"error":{"code":"...","message":"...","request_id":"..."}}`. Branch on `code`, not `message`.

| Status | Codes |
|--------|-------|
| 400 | `missing_param` `invalid_param` `unknown_param` `duplicate_param` `invalid_text` `invalid_encoding` `canvas_too_large` |
| 404 | `unknown_font` `not_found` |
| 405 | `method_not_allowed` |
| 503 | `overloaded` (retry after `Retry-After` seconds) |
| 500 | `internal` (quote `request_id` when reporting) |

Every response has `X-Request-Id` (a valid incoming one is echoed). One access-log line per request goes to
stdout. Request text is never logged.

Measured on a laptop (10+ cores, DejaVu fonts): ~27k req/s on cache hits, ~3k req/s on small uncached
renders, ~125 req/s on near-maximum 400px renders (single render worst case ~170 ms).
