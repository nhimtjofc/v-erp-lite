from __future__ import annotations

import zipfile
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn


ROOT = Path(__file__).resolve().parents[1]
DOCX = ROOT / "artifacts" / "V-ERP_Lite_Technical_Architecture_Review_2026-07-31.docx"


def dxa_value(node, attr="w:w"):
    value = node.get(qn(attr)) if node is not None else None
    return int(value) if value and value.lstrip("-").isdigit() else None


with zipfile.ZipFile(DOCX) as archive:
    bad_member = archive.testzip()
    assert bad_member is None, f"Corrupt ZIP member: {bad_member}"
    names = set(archive.namelist())
    required = {
        "[Content_Types].xml",
        "word/document.xml",
        "word/styles.xml",
        "word/numbering.xml",
        "word/settings.xml",
    }
    assert required.issubset(names), required - names

doc = Document(DOCX)
assert len(doc.sections) == 1
section = doc.sections[0]
assert round(section.page_width.inches, 2) == 8.50
assert round(section.page_height.inches, 2) == 11.00
assert all(
    round(value.inches, 2) == 1.00
    for value in (
        section.top_margin,
        section.right_margin,
        section.bottom_margin,
        section.left_margin,
    )
)

styles = doc.styles
assert styles["Normal"].font.name == "Calibri"
assert round(styles["Normal"].font.size.pt, 1) == 11.0
assert round(styles["Heading 1"].font.size.pt, 1) == 16.0
assert round(styles["Heading 2"].font.size.pt, 1) == 13.0
assert round(styles["Heading 3"].font.size.pt, 1) == 12.0

geometry_errors = []
fixed_heights = []
for table_index, table in enumerate(doc.tables, 1):
    tbl_pr = table._tbl.tblPr
    tbl_w = dxa_value(tbl_pr.find(qn("w:tblW")))
    tbl_ind = dxa_value(tbl_pr.find(qn("w:tblInd")))
    grid_widths = [dxa_value(col) for col in table._tbl.tblGrid.findall(qn("w:gridCol"))]
    if not grid_widths or any(width is None for width in grid_widths):
        geometry_errors.append((table_index, "missing grid widths"))
        continue
    if tbl_w != sum(grid_widths):
        geometry_errors.append((table_index, f"tblW={tbl_w}; grid={sum(grid_widths)}"))
    if tbl_ind not in (0, 120):
        geometry_errors.append((table_index, f"tblInd={tbl_ind}"))
    for row_index, row in enumerate(table.rows, 1):
        height = row._tr.get_or_add_trPr().find(qn("w:trHeight"))
        if height is not None:
            fixed_heights.append((table_index, row_index))
        if len(row.cells) == len(grid_widths):
            cell_widths = [
                dxa_value(cell._tc.get_or_add_tcPr().find(qn("w:tcW")))
                for cell in row.cells
            ]
            if cell_widths != grid_widths:
                geometry_errors.append(
                    (table_index, f"row {row_index}: cells={cell_widths}; grid={grid_widths}")
                )

assert not geometry_errors, geometry_errors[:10]
assert not fixed_heights, fixed_heights[:10]

all_text = "\n".join(paragraph.text for paragraph in doc.paragraphs)
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            all_text += "\n" + "\n".join(p.text for p in cell.paragraphs)

required_headings = [
    "Capable modular monolith; not yet production-safe for financial use",
    "Architecture context",
    "Payments, wallet, and escrow",
    "Priority risk register",
    "Target architecture",
]
for heading in required_headings:
    assert heading in all_text, heading

for forbidden in (
    "GOCSPX-",
    "v-erp-admin-secret",
    "chatbot-secret-v1",
    "mock_secret_v_erp_lite_2025",
):
    assert forbidden not in all_text, f"Secret leaked into report: {forbidden}"

assert "TODO" not in all_text
assert "[PLACEHOLDER]" not in all_text

fake_bullets = [
    p.text
    for p in doc.paragraphs
    if p.text.startswith(("• ", "- ", "* "))
    and p._p.get_or_add_pPr().find(qn("w:numPr")) is None
]
assert not fake_bullets, fake_bullets[:10]

numbered_paragraphs = [
    p for p in doc.paragraphs if p._p.get_or_add_pPr().find(qn("w:numPr")) is not None
]
assert len(numbered_paragraphs) >= 80

inline_images = doc._element.xpath(".//w:drawing")
doc_props = doc._element.xpath(".//wp:docPr")
assert len(inline_images) == 4
assert len(doc_props) == 4
for prop in doc_props:
    assert prop.get("descr"), "Missing image alt text"

print(
    "STRUCTURAL_AUDIT_OK",
    {
        "paragraphs": len(doc.paragraphs),
        "tables": len(doc.tables),
        "numbered_paragraphs": len(numbered_paragraphs),
        "images": len(inline_images),
        "bytes": DOCX.stat().st_size,
    },
)
