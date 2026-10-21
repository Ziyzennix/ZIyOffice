#!/usr/bin/env python3
"""Writes a PDF with an AcroForm, by hand.

The fixture is built outside the Kotlin code that reads it, so the two
cannot share a mistake. Regenerate with:

    python3 core/pdf/src/test/resources/libreoffice/make_form.py <out.pdf>
"""
import sys
import zlib


def build(objects, root, extra_trailer=b"", version=b"1.7"):
    """Assembles the objects into a file with a classic cross-reference."""
    out = bytearray()
    out += b"%PDF-" + version + b"\n%\xe2\xe3\xcf\xd3\n"
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += str(number).encode() + b" 0 obj\n" + body + b"\nendobj\n"

    xref_at = len(out)
    out += b"xref\n0 " + str(len(objects) + 1).encode() + b"\n"
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += ("%010d 00000 n \n" % offset).encode()
    out += b"trailer\n<< /Size " + str(len(objects) + 1).encode()
    out += b" /Root " + root.encode() + b" 0 R " + extra_trailer + b">>\n"
    out += b"startxref\n" + str(xref_at).encode() + b"\n%%EOF\n"
    return bytes(out)


def form_pdf():
    # 1 catalog, 2 pages, 3 page, 4 the text field, 5 the tick box,
    # 6 the list, 7 the form itself.
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Annots [4 0 R 5 0 R 6 0 R] >>",
        b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (full name) "
        b"/V (Ada Lovelace) /Rect [72 700 372 724] "
        b"/DA (/Helv 11 Tf 0 g) /F 4 >>",
        b"<< /Type /Annot /Subtype /Widget /FT /Btn /T (agree) "
        b"/V /Off /Rect [72 660 88 676] /AP << /N << /Yes << /Length 0 >> >> "
        b"/Off << /Length 0 >> >> >> /F 4 >>",
        b"<< /Type /Annot /Subtype /Widget /FT /Ch /T (country) /Ff 131072 "
        b"/V (Norway) /Opt [(Norway) (Sweden) (Denmark)] "
        b"/Rect [72 620 372 644] /DA (/Helv 11 Tf 0 g) /F 4 >>",
        b"<< /Fields [4 0 R 5 0 R 6 0 R] /DA (/Helv 0 Tf 0 g) "
        b"/NeedAppearances false >>",
    ]
    return build(objects, "1")


def plain_pdf(pages=3):
    """A document with a few pages and no form, for the parser tests."""
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>",
    ]
    for index in range(3):
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 7 0 R >> >> /Contents 8 0 R >>"
        )
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    body = b"BT /F1 18 Tf 72 700 Td (Page text) Tj ET"
    compressed = zlib.compress(body)
    objects.append(
        b"<< /Length " + str(len(compressed)).encode() + b" /Filter /FlateDecode >>\n"
        b"stream\n" + compressed + b"\nendstream"
    )
    return build(objects, "1")


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "form.pdf"
    with open(target, "wb") as handle:
        handle.write(form_pdf())
    plain = target.replace(".pdf", "-plain.pdf")
    with open(plain, "wb") as handle:
        handle.write(plain_pdf())
    print("wrote", target, "and", plain)
