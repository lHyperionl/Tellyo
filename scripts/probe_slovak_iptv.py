"""Probe playlist manifests, nested variants, keys/init files and one media segment.

This checks network delivery, not Android decoder/playback support. No credentials
or TLS verification bypasses are used. Run from the repository root:
  python scripts/probe_slovak_iptv.py --output artifacts/iptv/stream-probe.json
"""

import argparse
import concurrent.futures
import datetime
import json
import pathlib
import re
import urllib.error
import urllib.parse
import urllib.request


def playlist_entries(raw):
    entries, attributes, headers = [], None, {}
    for line in raw.splitlines():
        line = line.strip()
        if line.startswith("#EXTINF:"):
            attributes = dict(re.findall(r'([\w-]+)="([^"]*)"', line))
            headers = {}
        elif line.startswith("#EXTVLCOPT:"):
            option, _, value = line.removeprefix("#EXTVLCOPT:").partition("=")
            name = {"http-user-agent": "User-Agent", "http-referrer": "Referer"}.get(option)
            if name:
                headers[name] = value
        elif line and not line.startswith("#") and attributes is not None:
            entries.append({"id": attributes.get("tvg-id"),
                            "name": attributes.get("tvg-name"), "url": line,
                            "headers": headers.copy()})
            attributes = None
    return entries


def fetch(url, headers, limit):
    request = urllib.request.Request(url, headers={**headers, "Range": f"bytes=0-{limit-1}"})
    with urllib.request.urlopen(request, timeout=12) as response:
        return response.read(limit), response.url, response.status


def probe(entry):
    result = {**entry, "checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    if not urllib.parse.urlparse(entry["url"]).path.lower().endswith(".m3u8"):
        return {**result, "status": "unsupported", "detail": "Not an HLS manifest URL"}
    try:
        url, headers = entry["url"], entry["headers"]
        for _ in range(5):
            data, base, status = fetch(url, headers, 256 * 1024)
            text = data.decode("utf-8-sig")
            if not text.lstrip().startswith("#EXTM3U"):
                raise ValueError("Response is not an HLS manifest")
            lines = [line.strip() for line in text.splitlines() if line.strip()]
            media = [line for line in lines if not line.startswith("#")]
            if not media:
                raise ValueError("Manifest has no variants/segments")
            if any(line.startswith("#EXT-X-STREAM-INF:") for line in lines):
                url = urllib.parse.urljoin(base, media[0])
                continue
            for line in lines:
                if line.startswith(("#EXT-X-KEY:", "#EXT-X-MAP:")):
                    resource = re.search(r'URI="([^"]+)"', line)
                    if resource:
                        payload, _, _ = fetch(urllib.parse.urljoin(base, resource[1]), headers, 65536)
                        if not payload:
                            raise ValueError("Empty key/init resource")
            segment, _, segment_status = fetch(urllib.parse.urljoin(base, media[-1]), headers, 65536)
            if not segment:
                raise ValueError("Empty media segment")
            result.update(status="delivered", manifest_http=status, segment_http=segment_status,
                          segment_bytes=len(segment), detail="HLS manifest and media segment delivered")
            return result
        raise ValueError("Too many nested manifests")
    except urllib.error.HTTPError as error:
        result.update(status="failed", http_status=error.code, detail=f"HTTP {error.code}")
    except Exception as error:
        result.update(status="failed", detail=f"{type(error).__name__}: {error}")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--playlist", default="app/src/main/assets/slovak-iptv.m3u")
    parser.add_argument("--output", default="artifacts/iptv/stream-probe.json")
    args = parser.parse_args()
    entries = playlist_entries(pathlib.Path(args.playlist).read_text(encoding="utf-8-sig"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(probe, entries))
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    counts = {status: sum(row["status"] == status for row in results)
              for status in ("delivered", "failed", "unsupported")}
    print(json.dumps(counts))
    for row in results:
        print(f'{row["status"]:12} {row["name"]}: {row["detail"]}')


if __name__ == "__main__":
    main()
