from pathlib import Path
import zipfile

from docx import Document
from docx.oxml.ns import qn

root = Path(__file__).resolve().parents[1]
path = root / "artifacts" / "V-ERP_Lite_Technical_Architecture_Review_2026-07-31.docx"

with zipfile.ZipFile(path) as zf:
    assert zf.testzip() is None

doc = Document(path)
sec = doc.sections[0]
assert len(doc.sections) == 1
assert round(sec.page_width.inches, 2) == 8.50
assert round(sec.page_height.inches, 2) == 11.00
assert [round(x.inches, 2) for x in (
    sec.top_margin, sec.right_margin, sec.bottom_margin, sec.left_margin
)] == [1.0, 1.0, 1.0, 1.0]
assert doc.styles["Normal"].font.name == "Calibri"
assert round(doc.styles["Normal"].font.size.pt, 1) == 11.0

def dxa(node):
    if node is None:
        return None
    value = node.get(qn("w:w"))
    return int(value) if value and value.lstrip("-").isdigit() else None

errors = []
for ti, table in enumerate(doc.tables, 1):
    tbl_pr = table._tbl.tblPr
    tbl_w = dxa(tbl_pr.find(qn("w:tblW")))
    tbl_ind = dxa(tbl_pr.find(qn("w:tblInd")))
    grid = [dxa(x) for x in table._tbl.tblGrid.findall(qn("w:gridCol"))]
    if not grid or any(x is None for x in grid) or tbl_w != sum(grid):
        errors.append((ti, "table/grid width", tbl_w, grid))
    if tbl_ind not in (0, 120):
        errors.append((ti, "indent", tbl_ind))
    for ri, row in enumerate(table.rows, 1):
        assert row._tr.get_or_add_trPr().find(qn("w:trHeight")) is None
        if len(row.cells) == len(grid):
            widths = [dxa(c._tc.get_or_add_tcPr().find(qn("w:tcW"))) for c in row.cells]
            if widths != grid:
                errors.append((ti, ri, widths, grid))
assert not errors, errors[:10]

text = "\n".join(p.text for p in doc.paragraphs)
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            text += "\n" + "\n".join(p.text for p in cell.paragraphs)
folded = text.casefold()
for required in (
    "capable modular monolith; not yet production-safe for financial use",
    "architecture context",
    "payments, wallet, and escrow",
    "priority risk register",
    "target architecture",
):
    assert required in folded
for forbidden in (
    "gocspx-",
    "v-erp-admin-secret",
    "chatbot-secret-v1",
    "mock_secret_v_erp_lite_2025",
    "[placeholder]",
    "todo",
):
    assert forbidden not in folded

fake_bullets = [
    p.text for p in doc.paragraphs
    if p.text.startswith(("• ", "- ", "* "))
    and p._p.get_or_add_pPr().find(qn("w:numPr")) is None
]
assert not fake_bullets
numbered = [p for p in doc.paragraphs if p._p.get_or_add_pPr().find(qn("w:numPr")) is not None]
assert len(numbered) >= 80

drawings = doc._element.xpath(".//w:drawing")
doc_props = doc._element.xpath(".//wp:docPr")
assert len(drawings) == 4
assert len(doc_props) == 4
assert all(x.get("descr") for x in doc_props)

print("STRUCTURAL_AUDIT_OK", {
    "paragraphs": len(doc.paragraphs),
    "tables": len(doc.tables),
    "numbered": len(numbered),
    "images": len(drawings),
    "bytes": path.stat().st_size,
})
