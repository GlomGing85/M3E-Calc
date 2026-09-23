#!/usr/bin/env python3
"""Assemble the final APK.

Reads the APK produced by `aapt2 link` (resources.arsc + binary manifest +
compiled resources + assets), adds the dex file(s) produced by d8, and rewrites
the archive with 4-byte aligned STORED entries -- the same job `zipalign` does.
Android 11+ refuses to install an APK whose resources.arsc is compressed or
misaligned, so this step is not optional.
"""
import os
import sys
import zipfile

ALIGNMENT = 4
ALIGN_HEADER_ID = 0xD935  # same extra-field tag AOSP zipalign uses


def packed_size(info: zipfile.ZipInfo) -> int:
    return len(info.filename.encode("utf-8")) + 30


def copy_entry(zout: zipfile.ZipFile, name: str, data: bytes, compress: int,
               date_time, external_attr: int) -> None:
    info = zipfile.ZipInfo(name, date_time=date_time)
    info.compress_type = compress
    info.external_attr = external_attr
    info.create_system = 3
    if compress == zipfile.ZIP_STORED:
        # Position of the file data once the local header has been written.
        pos = zout.fp.tell() + packed_size(info)
        pad = (-(pos + 4)) % ALIGNMENT
        if (pos + pad) % ALIGNMENT != 0:
            pad = (ALIGNMENT - (pos % ALIGNMENT)) % ALIGNMENT
        if pad or (pos % ALIGNMENT != 0):
            data_len = (-(pos + 4)) % ALIGNMENT
            info.extra = (ALIGN_HEADER_ID.to_bytes(2, "little")
                          + data_len.to_bytes(2, "little")
                          + b"\x00" * data_len)
    zout.writestr(info, data)


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: pack-apk.py <base.apk> <out.apk> <dex...>", file=sys.stderr)
        return 2
    base, out = sys.argv[1], sys.argv[2]
    dexes = sys.argv[3:]

    with zipfile.ZipFile(base) as zin:
        names = zin.namelist()
        entries = [(n, zin.read(n), zin.getinfo(n)) for n in names]

    # classes.dex must come first-ish and resources.arsc must stay STORED.
    order = sorted(entries, key=lambda e: (e[0] != "AndroidManifest.xml",
                                           e[0] != "resources.arsc", e[0]))

    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for idx, dex in enumerate(dexes):
            with open(dex, "rb") as fh:
                copy_entry(zout, "classes.dex" if idx == 0 else f"classes{idx + 1}.dex",
                           fh.read(), zipfile.ZIP_DEFLATED, (1980, 1, 1, 0, 0, 0),
                           0o100644 << 16)
        for name, data, info in order:
            compress = zipfile.ZIP_STORED if name == "resources.arsc" else info.compress_type
            copy_entry(zout, name, data, compress, info.date_time, info.external_attr)

    # Sanity check the alignment we just wrote.
    with zipfile.ZipFile(out) as z:
        for info in z.infolist():
            if info.compress_type == zipfile.ZIP_STORED:
                off = info.header_offset + packed_size(info) + len(info.extra)
                if off % ALIGNMENT != 0:
                    print(f"ERROR: {info.filename} is not aligned ({off})", file=sys.stderr)
                    return 1
    print(f"packed {out} ({os.path.getsize(out)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
