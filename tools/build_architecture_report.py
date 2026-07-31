from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "artifacts"
QA_ASSET_DIR = ROOT / ".docx-qa-assets"
OUT_PATH = OUT_DIR / "V-ERP_Lite_Technical_Architecture_Review_2026-07-31.docx"

OUT_DIR.mkdir(parents=True, exist_ok=True)
QA_ASSET_DIR.mkdir(parents=True, exist_ok=True)


# ---------------------------------------------------------------------------
# Resolved design tokens
# Preset: standard_business_brief
# First-page pattern: editorial_cover
# Named overrides:
#   - cover_title: 32 pt, navy
#   - cover_metric_strip: 3 equal columns, pale blue fill
#   - diagram_caption: 8.5 pt, muted, centered
#   - evidence_code: Consolas 8.5 pt
# ---------------------------------------------------------------------------

PAGE_WIDTH_IN = 8.5
PAGE_HEIGHT_IN = 11
MARGIN_IN = 1.0
CONTENT_WIDTH_DXA = 9360
TABLE_INDENT_DXA = 120
CELL_MARGINS = {"top": 80, "bottom": 80, "start": 120, "end": 120}

FONT_BODY = "Calibri"
FONT_CODE = "Consolas"
NAVY = "0B2545"
BLUE = "2E74B5"
DARK_BLUE = "1F4D78"
TEAL = "157A6E"
GOLD = "A66F00"
INK = "172033"
MUTED = "5B677A"
LIGHT_BLUE = "E8EEF5"
LIGHT_GRAY = "F2F4F7"
PALE_BLUE = "F4F7FB"
PALE_GREEN = "EAF5F2"
PALE_GOLD = "FFF6DF"
PALE_RED = "FDEEEE"
GREEN = "2F6F5E"
ORANGE = "B45500"
RED = "9B1C1C"
WHITE = "FFFFFF"
BORDER = "D6DEE8"


def rgb(hex_value: str) -> RGBColor:
    return RGBColor.from_string(hex_value)


def set_run_font(run, name=FONT_BODY, size=None, color=INK, bold=None, italic=None):
    run.font.name = name
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if color is not None:
        run.font.color.rgb = rgb(color)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def set_cell_shading(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)
    shd.set(qn("w:val"), "clear")


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.find(qn("w:tcMar"))
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for side, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{side}"))
        if node is None:
            node = OxmlElement(f"w:{side}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_table_borders(table, color=BORDER, size=6, inside=True):
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    sides = ["top", "left", "bottom", "right"]
    if inside:
        sides += ["insideH", "insideV"]
    for side in sides:
        edge = borders.find(qn(f"w:{side}"))
        if edge is None:
            edge = OxmlElement(f"w:{side}")
            borders.append(edge)
        edge.set(qn("w:val"), "single")
        edge.set(qn("w:sz"), str(size))
        edge.set(qn("w:space"), "0")
        edge.set(qn("w:color"), color)


def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    header = tr_pr.find(qn("w:tblHeader"))
    if header is None:
        header = OxmlElement("w:tblHeader")
        tr_pr.append(header)
    header.set(qn("w:val"), "true")


def set_table_geometry(table, widths_dxa: Sequence[int], indent_dxa=TABLE_INDENT_DXA):
    total = sum(widths_dxa)
    tbl_pr = table._tbl.tblPr

    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(total))
    tbl_w.set(qn("w:type"), "dxa")

    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), str(indent_dxa))
    tbl_ind.set(qn("w:type"), "dxa")

    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")

    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths_dxa:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)

    for row in table.rows:
        for idx, cell in enumerate(row.cells):
            width = widths_dxa[min(idx, len(widths_dxa) - 1)]
            tc_pr = cell._tc.get_or_add_tcPr()
            tc_w = tc_pr.find(qn("w:tcW"))
            if tc_w is None:
                tc_w = OxmlElement("w:tcW")
                tc_pr.append(tc_w)
            tc_w.set(qn("w:w"), str(width))
            tc_w.set(qn("w:type"), "dxa")
            set_cell_margins(cell, **CELL_MARGINS)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER

    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT


def set_paragraph_bottom_border(paragraph, color=BLUE, size=10):
    p_pr = paragraph._p.get_or_add_pPr()
    p_bdr = p_pr.find(qn("w:pBdr"))
    if p_bdr is None:
        p_bdr = OxmlElement("w:pBdr")
        p_pr.append(p_bdr)
    bottom = p_bdr.find(qn("w:bottom"))
    if bottom is None:
        bottom = OxmlElement("w:bottom")
        p_bdr.append(bottom)
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), "5")
    bottom.set(qn("w:color"), color)


def add_field(run, field_code: str, display_text: str = ""):
    fld_char_begin = OxmlElement("w:fldChar")
    fld_char_begin.set(qn("w:fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = field_code
    fld_char_sep = OxmlElement("w:fldChar")
    fld_char_sep.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = display_text
    fld_char_end = OxmlElement("w:fldChar")
    fld_char_end.set(qn("w:fldCharType"), "end")
    for node in (fld_char_begin, instr_text, fld_char_sep, text, fld_char_end):
        run._r.append(node)


def add_page_number(paragraph):
    run = paragraph.add_run()
    set_run_font(run, size=8.5, color=MUTED)
    add_field(run, "PAGE", "1")


def set_picture_alt(paragraph, alt_text: str):
    for doc_pr in paragraph._p.xpath(".//wp:docPr"):
        doc_pr.set("descr", alt_text)
        doc_pr.set("title", alt_text)


def configure_styles(doc: Document):
    styles = doc.styles

    normal = styles["Normal"]
    normal.font.name = FONT_BODY
    normal._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_BODY)
    normal.font.size = Pt(11)
    normal.font.color.rgb = rgb(INK)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.10

    title = styles["Title"]
    title.font.name = FONT_BODY
    title._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
    title._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
    title.font.size = Pt(32)
    title.font.bold = True
    title.font.color.rgb = rgb(NAVY)
    title.paragraph_format.space_before = Pt(0)
    title.paragraph_format.space_after = Pt(8)
    title.paragraph_format.keep_with_next = True

    subtitle = styles["Subtitle"]
    subtitle.font.name = FONT_BODY
    subtitle._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
    subtitle._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
    subtitle.font.size = Pt(15)
    subtitle.font.color.rgb = rgb(MUTED)
    subtitle.paragraph_format.space_before = Pt(0)
    subtitle.paragraph_format.space_after = Pt(12)
    subtitle.paragraph_format.line_spacing = 1.1
    subtitle.paragraph_format.keep_with_next = True

    for name, size, color, before, after in (
        ("Heading 1", 16, BLUE, 16, 8),
        ("Heading 2", 13, BLUE, 12, 6),
        ("Heading 3", 12, DARK_BLUE, 8, 4),
    ):
        st = styles[name]
        st.font.name = FONT_BODY
        st._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
        st._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
        st._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_BODY)
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = rgb(color)
        st.paragraph_format.space_before = Pt(before)
        st.paragraph_format.space_after = Pt(after)
        st.paragraph_format.line_spacing = 1.0
        st.paragraph_format.keep_with_next = True
        st.paragraph_format.keep_together = True

    caption = styles["Caption"]
    caption.font.name = FONT_BODY
    caption._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
    caption._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
    caption.font.size = Pt(8.5)
    caption.font.italic = True
    caption.font.color.rgb = rgb(MUTED)
    caption.paragraph_format.space_before = Pt(4)
    caption.paragraph_format.space_after = Pt(8)
    caption.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.paragraph_format.keep_with_next = False

    if "Code Block" not in styles:
        styles.add_style("Code Block", 1)
    code = styles["Code Block"]
    code.font.name = FONT_CODE
    code._element.rPr.rFonts.set(qn("w:ascii"), FONT_CODE)
    code._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_CODE)
    code.font.size = Pt(8.5)
    code.font.color.rgb = rgb(INK)
    code.paragraph_format.space_before = Pt(3)
    code.paragraph_format.space_after = Pt(6)
    code.paragraph_format.line_spacing = 1.0
    code.paragraph_format.left_indent = Inches(0.18)
    code.paragraph_format.right_indent = Inches(0.12)

    if "Small Note" not in styles:
        styles.add_style("Small Note", 1)
    note = styles["Small Note"]
    note.font.name = FONT_BODY
    note._element.rPr.rFonts.set(qn("w:ascii"), FONT_BODY)
    note._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_BODY)
    note.font.size = Pt(8.5)
    note.font.color.rgb = rgb(MUTED)
    note.paragraph_format.space_before = Pt(4)
    note.paragraph_format.space_after = Pt(4)
    note.paragraph_format.line_spacing = 1.05


def create_numbering(doc: Document):
    numbering_part = doc.part.numbering_part
    numbering = numbering_part.element

    def next_id(tag: str, attr: str) -> int:
        values = []
        for node in numbering.findall(qn(tag)):
            value = node.get(qn(attr))
            if value and value.isdigit():
                values.append(int(value))
        return max(values, default=0) + 1

    def make_num(fmt: str, text: str, font: str | None = None):
        abstract_id = next_id("w:abstractNum", "w:abstractNumId")
        abstract = OxmlElement("w:abstractNum")
        abstract.set(qn("w:abstractNumId"), str(abstract_id))

        multi = OxmlElement("w:multiLevelType")
        multi.set(qn("w:val"), "singleLevel")
        abstract.append(multi)

        lvl = OxmlElement("w:lvl")
        lvl.set(qn("w:ilvl"), "0")
        start = OxmlElement("w:start")
        start.set(qn("w:val"), "1")
        num_fmt = OxmlElement("w:numFmt")
        num_fmt.set(qn("w:val"), fmt)
        lvl_text = OxmlElement("w:lvlText")
        lvl_text.set(qn("w:val"), text)
        suff = OxmlElement("w:suff")
        suff.set(qn("w:val"), "tab")
        lvl_jc = OxmlElement("w:lvlJc")
        lvl_jc.set(qn("w:val"), "left")

        p_pr = OxmlElement("w:pPr")
        tabs = OxmlElement("w:tabs")
        tab = OxmlElement("w:tab")
        tab.set(qn("w:val"), "num")
        tab.set(qn("w:pos"), "720")
        tabs.append(tab)
        ind = OxmlElement("w:ind")
        ind.set(qn("w:left"), "720")
        ind.set(qn("w:hanging"), "360")
        spacing = OxmlElement("w:spacing")
        spacing.set(qn("w:after"), "160")
        spacing.set(qn("w:line"), "280")
        spacing.set(qn("w:lineRule"), "auto")
        p_pr.extend([tabs, ind, spacing])

        lvl.extend([start, num_fmt, lvl_text, suff, lvl_jc, p_pr])
        if font:
            r_pr = OxmlElement("w:rPr")
            r_fonts = OxmlElement("w:rFonts")
            r_fonts.set(qn("w:ascii"), font)
            r_fonts.set(qn("w:hAnsi"), font)
            r_pr.append(r_fonts)
            lvl.append(r_pr)
        abstract.append(lvl)
        numbering.append(abstract)

        num_id = next_id("w:num", "w:numId")
        num = OxmlElement("w:num")
        num.set(qn("w:numId"), str(num_id))
        abstract_ref = OxmlElement("w:abstractNumId")
        abstract_ref.set(qn("w:val"), str(abstract_id))
        num.append(abstract_ref)
        numbering.append(num)
        return num_id

    return {
        "bullet": make_num("bullet", "•", "Symbol"),
        "decimal": make_num("decimal", "%1."),
    }


def set_numbering(paragraph, num_id: int):
    p_pr = paragraph._p.get_or_add_pPr()
    num_pr = p_pr.find(qn("w:numPr"))
    if num_pr is None:
        num_pr = OxmlElement("w:numPr")
        p_pr.append(num_pr)
    ilvl = OxmlElement("w:ilvl")
    ilvl.set(qn("w:val"), "0")
    num = OxmlElement("w:numId")
    num.set(qn("w:val"), str(num_id))
    num_pr.extend([ilvl, num])


def add_bullet(doc, numbering, text, bold_prefix=None, color=INK):
    p = doc.add_paragraph()
    set_numbering(p, numbering["bullet"])
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.167
    p.paragraph_format.keep_together = True
    if bold_prefix and text.startswith(bold_prefix):
        r1 = p.add_run(bold_prefix)
        set_run_font(r1, size=11, color=color, bold=True)
        r2 = p.add_run(text[len(bold_prefix):])
        set_run_font(r2, size=11, color=color)
    else:
        r = p.add_run(text)
        set_run_font(r, size=11, color=color)
    return p


def add_number(doc, numbering, text, bold_prefix=None):
    p = doc.add_paragraph()
    set_numbering(p, numbering["decimal"])
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.167
    p.paragraph_format.keep_together = True
    if bold_prefix and text.startswith(bold_prefix):
        r1 = p.add_run(bold_prefix)
        set_run_font(r1, size=11, color=INK, bold=True)
        r2 = p.add_run(text[len(bold_prefix):])
        set_run_font(r2, size=11, color=INK)
    else:
        r = p.add_run(text)
        set_run_font(r, size=11)
    return p


def add_body(doc, text, bold_prefix=None, italic=False, color=INK, after=6):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.10
    if bold_prefix and text.startswith(bold_prefix):
        r1 = p.add_run(bold_prefix)
        set_run_font(r1, size=11, color=color, bold=True, italic=italic)
        r2 = p.add_run(text[len(bold_prefix):])
        set_run_font(r2, size=11, color=color, italic=italic)
    else:
        r = p.add_run(text)
        set_run_font(r, size=11, color=color, italic=italic)
    return p


def add_code(doc, text):
    p = doc.add_paragraph(style="Code Block")
    p.paragraph_format.keep_together = True
    r = p.add_run(text)
    set_run_font(r, name=FONT_CODE, size=8.5, color=INK)
    p_pr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), LIGHT_GRAY)
    p_pr.append(shd)
    return p


def add_kicker(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text.upper())
    set_run_font(r, size=9.5, color=GOLD, bold=True)
    r.font.all_caps = True
    return p


def add_section_title(doc, kicker, title, subtitle=None):
    add_kicker(doc, kicker)
    h = doc.add_paragraph(title, style="Heading 1")
    h.paragraph_format.space_before = Pt(0)
    if subtitle:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(10)
        p.paragraph_format.keep_with_next = True
        r = p.add_run(subtitle)
        set_run_font(r, size=11.5, color=MUTED, italic=True)
    return h


def add_callout(doc, label, text, fill=PALE_BLUE, label_color=BLUE):
    table = doc.add_table(rows=1, cols=1)
    set_table_geometry(table, [CONTENT_WIDTH_DXA])
    set_table_borders(table, color=BORDER, size=6, inside=False)
    cell = table.cell(0, 0)
    set_cell_shading(cell, fill)
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.10
    p.paragraph_format.keep_together = True
    r1 = p.add_run(label.upper() + "  ")
    set_run_font(r1, size=9, color=label_color, bold=True)
    r2 = p.add_run(text)
    set_run_font(r2, size=10.5, color=INK)
    after = doc.add_paragraph()
    after.paragraph_format.space_after = Pt(2)
    return table


def add_table(
    doc,
    headers: Sequence[str],
    rows: Sequence[Sequence[str]],
    widths_dxa: Sequence[int],
    header_fill=LIGHT_BLUE,
    font_size=9.2,
    first_col_bold=False,
    alignments: Sequence[int] | None = None,
):
    table = doc.add_table(rows=1, cols=len(headers))
    set_table_geometry(table, widths_dxa)
    set_table_borders(table, color=BORDER, size=6, inside=True)
    hdr = table.rows[0]
    set_repeat_table_header(hdr)
    for idx, value in enumerate(headers):
        cell = hdr.cells[idx]
        set_cell_shading(cell, header_fill)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT if not alignments else alignments[idx]
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.05
        r = p.add_run(value)
        set_run_font(r, size=9.2, color=NAVY, bold=True)

    for row_idx, values in enumerate(rows):
        cells = table.add_row().cells
        for idx, value in enumerate(values):
            cell = cells[idx]
            if row_idx % 2 == 1:
                set_cell_shading(cell, "FAFBFC")
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT if not alignments else alignments[idx]
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(1)
            p.paragraph_format.line_spacing = 1.08
            r = p.add_run(str(value))
            set_run_font(r, size=font_size, color=INK, bold=(first_col_bold and idx == 0))

    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(2)
    return table


def add_metric_strip(doc, metrics: Sequence[tuple[str, str, str]]):
    widths = [3120, 3120, 3120]
    table = doc.add_table(rows=1, cols=3)
    set_table_geometry(table, widths, indent_dxa=0)
    set_table_borders(table, color=BORDER, size=7, inside=True)
    for idx, (value, label, note) in enumerate(metrics):
        cell = table.cell(0, idx)
        set_cell_shading(cell, PALE_BLUE)
        p1 = cell.paragraphs[0]
        p1.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p1.paragraph_format.space_after = Pt(2)
        r1 = p1.add_run(value)
        set_run_font(r1, size=20, color=NAVY, bold=True)
        p2 = cell.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p2.paragraph_format.space_after = Pt(1)
        r2 = p2.add_run(label)
        set_run_font(r2, size=8.7, color=BLUE, bold=True)
        p3 = cell.add_paragraph()
        p3.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p3.paragraph_format.space_after = Pt(1)
        r3 = p3.add_run(note)
        set_run_font(r3, size=7.8, color=MUTED)
    return table


def add_score_bar(doc, label, score, color):
    table = doc.add_table(rows=1, cols=3)
    set_table_geometry(table, [2700, 5600, 1060])
    set_table_borders(table, color=WHITE, size=0, inside=False)
    table.cell(0, 0).text = ""
    p = table.cell(0, 0).paragraphs[0]
    r = p.add_run(label)
    set_run_font(r, size=9.5, color=INK, bold=True)
    p.paragraph_format.space_after = Pt(0)

    bar = table.cell(0, 1)
    set_cell_shading(bar, LIGHT_GRAY)
    pbar = bar.paragraphs[0]
    pbar.paragraph_format.space_after = Pt(0)
    pbar.paragraph_format.line_spacing = 0.8
    run = pbar.add_run("■" * score + "□" * (10 - score))
    set_run_font(run, name="Arial", size=9, color=color, bold=True)

    ps = table.cell(0, 2).paragraphs[0]
    ps.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    ps.paragraph_format.space_after = Pt(0)
    rs = ps.add_run(f"{score}/10")
    set_run_font(rs, size=9.5, color=color, bold=True)
    return table


def page_break(doc):
    p = doc.add_paragraph()
    p.add_run().add_break(WD_BREAK.PAGE)


def set_document_properties(doc: Document):
    props = doc.core_properties
    props.title = "V-ERP Lite Technical Architecture Review"
    props.subject = "Workspace-wide architecture, codebase, security, integration, and delivery analysis"
    props.author = "OpenAI Codex"
    props.keywords = "V-ERP Lite, Quarkus, React, MariaDB, Keycloak, WooCommerce, architecture review"
    props.comments = "Generated from the tracked workspace state on 31 July 2026."


def configure_section(section, first=False):
    section.page_width = Inches(PAGE_WIDTH_IN)
    section.page_height = Inches(PAGE_HEIGHT_IN)
    section.top_margin = Inches(MARGIN_IN)
    section.bottom_margin = Inches(MARGIN_IN)
    section.left_margin = Inches(MARGIN_IN)
    section.right_margin = Inches(MARGIN_IN)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)
    section.different_first_page_header_footer = first


def configure_header_footer(section):
    header = section.header
    p = header.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_after = Pt(0)
    left = p.add_run("V-ERP LITE  |  TECHNICAL ARCHITECTURE REVIEW")
    set_run_font(left, size=8.2, color=MUTED, bold=True)
    p.add_run("\t")
    right = p.add_run("WORKSPACE ANALYSIS  •  31 JUL 2026")
    set_run_font(right, size=8.2, color=MUTED)
    tabs = p.paragraph_format.tab_stops
    tabs.add_tab_stop(Inches(6.5), WD_ALIGN_PARAGRAPH.RIGHT)

    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    left = p.add_run("Architecture review — internal engineering working document")
    set_run_font(left, size=8.2, color=MUTED)
    p.add_run("\t")
    add_page_number(p)
    tabs = p.paragraph_format.tab_stops
    tabs.add_tab_stop(Inches(6.5), WD_ALIGN_PARAGRAPH.RIGHT)


def get_font(size, bold=False):
    candidates = [
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
        Path("C:/Windows/Fonts/calibrib.ttf" if bold else "C:/Windows/Fonts/calibri.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def rounded_box(draw, xy, fill, outline, title, lines, accent=None, title_size=28, body_size=20):
    x1, y1, x2, y2 = xy
    draw.rounded_rectangle(xy, radius=22, fill=fill, outline=outline, width=3)
    if accent:
        draw.rounded_rectangle((x1, y1, x1 + 12, y2), radius=6, fill=accent)
    title_font = get_font(title_size, bold=True)
    body_font = get_font(body_size, bold=False)
    draw.text((x1 + 28, y1 + 20), title, font=title_font, fill="#" + NAVY)
    y = y1 + 65
    for line in lines:
        draw.text((x1 + 30, y), line, font=body_font, fill="#" + MUTED)
        y += body_size + 12


def arrow(draw, start, end, color=BLUE, width=5, label=None):
    draw.line([start, end], fill="#" + color, width=width)
    x2, y2 = end
    x1, y1 = start
    dx, dy = x2 - x1, y2 - y1
    length = max((dx * dx + dy * dy) ** 0.5, 1)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    tip = (x2, y2)
    left = (x2 - 20 * ux + 10 * px, y2 - 20 * uy + 10 * py)
    right = (x2 - 20 * ux - 10 * px, y2 - 20 * uy - 10 * py)
    draw.polygon([tip, left, right], fill="#" + color)
    if label:
        font = get_font(17, bold=True)
        bbox = draw.textbbox((0, 0), label, font=font)
        tx = (x1 + x2) / 2 - (bbox[2] - bbox[0]) / 2
        ty = (y1 + y2) / 2 - 26
        draw.rounded_rectangle((tx - 8, ty - 3, tx + bbox[2] - bbox[0] + 8, ty + 24), 7, fill="white")
        draw.text((tx, ty), label, font=font, fill="#" + color)


def make_context_diagram(path: Path):
    img = Image.new("RGB", (1600, 850), "white")
    draw = ImageDraw.Draw(img)
    title = get_font(34, bold=True)
    draw.text((60, 35), "V-ERP Lite system context", font=title, fill="#" + NAVY)

    rounded_box(draw, (60, 180, 360, 390), "#F4F7FB", "#" + BORDER, "People", [
        "Admin / operator", "Business owner", "Customer / buyer"
    ], accent="#" + BLUE)
    rounded_box(draw, (560, 150, 1040, 430), "#E8EEF5", "#" + BLUE, "V-ERP Lite", [
        "React SPA", "Quarkus modular monolith", "Commerce + payments + tenant admin"
    ], accent="#" + NAVY, title_size=34, body_size=22)
    rounded_box(draw, (1240, 95, 1540, 265), "#EAF5F2", "#" + TEAL, "Keycloak", [
        "OIDC / PKCE", "Realm roles + org claim"
    ], accent="#" + TEAL)
    rounded_box(draw, (1240, 325, 1540, 495), "#FFF6DF", "#" + GOLD, "WooCommerce", [
        "Storefront", "Catalog / order connector"
    ], accent="#" + GOLD)
    rounded_box(draw, (1240, 555, 1540, 735), "#FDEEEE", "#" + RED, "AI services", [
        "FastAPI + Gemini", "Pinecone retrieval"
    ], accent="#" + RED)
    rounded_box(draw, (560, 570, 1040, 750), "#F2F4F7", "#" + BORDER, "Data plane", [
        "MariaDB + Flyway", "Local uploads + webhook/event logs"
    ], accent="#" + MUTED)

    arrow(draw, (360, 285), (560, 285), label="browser")
    arrow(draw, (1040, 220), (1240, 180), label="OIDC")
    arrow(draw, (1040, 320), (1240, 405), label="HMAC / REST")
    arrow(draw, (1040, 385), (1240, 645), label="HTTP")
    arrow(draw, (800, 430), (800, 570), label="JPA")
    img.save(path, quality=95)


def make_runtime_diagram(path: Path):
    img = Image.new("RGB", (1600, 920), "white")
    draw = ImageDraw.Draw(img)
    title = get_font(34, bold=True)
    draw.text((60, 35), "Local runtime topology and trust boundaries", font=title, fill="#" + NAVY)

    draw.rounded_rectangle((45, 110, 1555, 845), radius=28, fill="#FAFBFC", outline="#" + BORDER, width=3)
    draw.text((70, 125), "Developer workstation / Docker Desktop", font=get_font(20, bold=True), fill="#" + MUTED)

    rounded_box(draw, (85, 230, 355, 410), "#F4F7FB", "#" + BLUE, "Browser", [
        "SPA :5174", "Tokens in localStorage"
    ], accent="#" + BLUE)
    rounded_box(draw, (500, 185, 875, 440), "#E8EEF5", "#" + NAVY, "Quarkus API", [
        ":8081 /api", "147 resource operations", "Scheduler + Keycloak admin client"
    ], accent="#" + NAVY, title_size=32)
    rounded_box(draw, (1040, 150, 1450, 330), "#EAF5F2", "#" + TEAL, "Keycloak 26.6.1", [
        ":8181", "MariaDB-backed realm"
    ], accent="#" + TEAL)
    rounded_box(draw, (1040, 390, 1450, 580), "#F2F4F7", "#" + BORDER, "MariaDB 11.4", [
        ":3307", "ERP schema + Flyway V1–V58"
    ], accent="#" + MUTED)
    rounded_box(draw, (85, 590, 405, 780), "#FFF6DF", "#" + GOLD, "WordPress / Woo", [
        ":8090", "Separate MariaDB volume"
    ], accent="#" + GOLD)
    rounded_box(draw, (520, 595, 875, 780), "#FDEEEE", "#" + RED, "AI assistant", [
        "FastAPI :8000", "Gemini + Pinecone"
    ], accent="#" + RED)
    rounded_box(draw, (1040, 640, 1450, 790), "#FFF6DF", "#" + ORANGE, "Mock bank", [
        "Node :3000", "Callback → Quarkus webhook"
    ], accent="#" + ORANGE)

    arrow(draw, (355, 300), (500, 300), label="REST")
    arrow(draw, (875, 260), (1040, 240), label="OIDC/admin")
    arrow(draw, (875, 375), (1040, 475), label="JDBC")
    arrow(draw, (405, 680), (500, 390), label="connector")
    arrow(draw, (520, 680), (355, 390), label="chat UI")
    arrow(draw, (1040, 710), (875, 400), label="webhook")
    img.save(path, quality=95)


def make_payment_flow(path: Path):
    img = Image.new("RGB", (1600, 820), "white")
    draw = ImageDraw.Draw(img)
    title = get_font(34, bold=True)
    draw.text((60, 30), "Primary payment and escrow flow", font=title, fill="#" + NAVY)

    steps = [
        ("1", "Checkout", "Order snapshots\nprice, tax, stock", BLUE),
        ("2", "Intent", "Idempotency key\n+ amount/currency", TEAL),
        ("3", "Provider", "Mock bank / QR\n+ signed callback", GOLD),
        ("4", "Settlement", "Platform wallet\n+ seller escrow", NAVY),
        ("5", "Fulfillment", "Owner decision\n+ delivery", ORANGE),
        ("6", "Release", "Escrow → seller\nor refund", GREEN),
    ]
    x = 65
    for idx, (num, label, detail, color) in enumerate(steps):
        x1, y1, x2, y2 = x, 220, x + 210, 530
        draw.rounded_rectangle((x1, y1, x2, y2), radius=24, fill="#FAFBFC", outline="#" + color, width=4)
        draw.ellipse((x1 + 70, y1 - 55, x1 + 140, y1 + 15), fill="#" + color)
        nfont = get_font(30, bold=True)
        nb = draw.textbbox((0, 0), num, font=nfont)
        draw.text((x1 + 105 - (nb[2] - nb[0]) / 2, y1 - 43), num, font=nfont, fill="white")
        lfont = get_font(27, bold=True)
        lb = draw.textbbox((0, 0), label, font=lfont)
        draw.text((x1 + 105 - (lb[2] - lb[0]) / 2, y1 + 40), label, font=lfont, fill="#" + NAVY)
        dfont = get_font(20)
        yy = y1 + 115
        for line in detail.split("\n"):
            db = draw.textbbox((0, 0), line, font=dfont)
            draw.text((x1 + 105 - (db[2] - db[0]) / 2, yy), line, font=dfont, fill="#" + MUTED)
            yy += 34
        if idx < len(steps) - 1:
            arrow(draw, (x2 + 5, 375), (x2 + 45, 375), color=color, width=5)
        x += 250

    draw.rounded_rectangle((130, 625, 1470, 735), radius=20, fill="#FDEEEE", outline="#" + RED, width=2)
    draw.text((165, 650), "Release gate:", font=get_font(22, bold=True), fill="#" + RED)
    draw.text(
        (330, 650),
        "provider authenticity, one successful intent per order, immutable settled totals, and auditable ledger invariants",
        font=get_font(21),
        fill="#" + INK,
    )
    img.save(path, quality=95)


def make_tenant_diagram(path: Path):
    img = Image.new("RGB", (1600, 800), "white")
    draw = ImageDraw.Draw(img)
    title = get_font(34, bold=True)
    draw.text((60, 35), "Tenant and authorization decision path", font=title, fill="#" + NAVY)

    rounded_box(draw, (70, 180, 340, 400), "#EAF5F2", "#" + TEAL, "OIDC token", [
        "realm roles", "organization / tenant_code", "subject + email"
    ], accent="#" + TEAL)
    rounded_box(draw, (495, 180, 830, 400), "#E8EEF5", "#" + BLUE, "CallerContext", [
        "resolve tenant", "resolve DB user", "role checks"
    ], accent="#" + BLUE)
    rounded_box(draw, (980, 120, 1510, 315), "#F4F7FB", "#" + NAVY, "Resource / service query", [
        "id + tenantId", "business scope when applicable"
    ], accent="#" + NAVY)
    rounded_box(draw, (980, 455, 1510, 650), "#FDEEEE", "#" + RED, "Current failure modes", [
        "azp/default fallback", "role breadth", "staff business fallback"
    ], accent="#" + RED)
    arrow(draw, (340, 290), (495, 290), label="claims")
    arrow(draw, (830, 265), (980, 220), label="allow")
    arrow(draw, (665, 400), (980, 540), color=RED, label="fallback")
    draw.rounded_rectangle((140, 535, 830, 665), radius=18, fill="#FFF6DF", outline="#" + GOLD, width=2)
    draw.text((170, 558), "Target posture", font=get_font(23, bold=True), fill="#" + GOLD)
    draw.text((170, 600), "Fail closed → explicit tenant → explicit business → scoped query", font=get_font(22), fill="#" + INK)
    img.save(path, quality=95)


def add_figure(doc, image_path: Path, caption: str, alt: str, width=6.35):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.keep_with_next = True
    p.add_run().add_picture(str(image_path), width=Inches(width))
    set_picture_alt(p, alt)
    cap = doc.add_paragraph(caption, style="Caption")
    return p, cap


def build_document():
    context_img = QA_ASSET_DIR / "system-context.png"
    runtime_img = QA_ASSET_DIR / "runtime-topology.png"
    payment_img = QA_ASSET_DIR / "payment-flow.png"
    tenant_img = QA_ASSET_DIR / "tenant-auth.png"
    make_context_diagram(context_img)
    make_runtime_diagram(runtime_img)
    make_payment_flow(payment_img)
    make_tenant_diagram(tenant_img)

    doc = Document()
    set_document_properties(doc)
    configure_styles(doc)
    configure_section(doc.sections[0], first=True)
    configure_header_footer(doc.sections[0])
    numbering = create_numbering(doc)

    # Cover
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(74)
    add_kicker(doc, "Workspace-wide engineering review")
    p = doc.add_paragraph(style="Title")
    p.add_run("V-ERP Lite")
    p2 = doc.add_paragraph()
    p2.paragraph_format.space_after = Pt(8)
    p2.paragraph_format.keep_with_next = True
    r = p2.add_run("Technical Architecture & Software Assessment")
    set_run_font(r, size=24, color=NAVY, bold=True)
    sub = doc.add_paragraph(style="Subtitle")
    sub.add_run(
        "A senior-architect review of the current backend, frontend, data model, "
        "WooCommerce integration, payment system, AI assistant, security posture, and delivery controls."
    )

    rule = doc.add_paragraph()
    rule.paragraph_format.space_after = Pt(18)
    set_paragraph_bottom_border(rule, color=GOLD, size=12)

    add_metric_strip(doc, [
        ("540", "TRACKED FILES", "repository-wide inventory"),
        ("147", "REST OPERATIONS", "current resource extraction"),
        ("175", "BACKEND TESTS", "@Test methods in source"),
    ])

    add_body(
        doc,
        "Review date: 31 July 2026  |  Scope: current open workspace  |  Evidence: tracked source, configuration, migrations, tests, and documentation",
        color=MUTED,
        after=4,
    ).alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_body(
        doc,
        "Assessment lens: architecture coherence, tenant isolation, financial correctness, integration reliability, security, test confidence, and operational readiness.",
        color=MUTED,
        italic=True,
        after=0,
    ).alignment = WD_ALIGN_PARAGRAPH.CENTER

    page_break(doc)

    # Executive summary
    add_section_title(
        doc,
        "Executive brief",
        "Capable modular monolith; not yet production-safe for financial use",
        "The repository has strong breadth and visible engineering momentum, but release-critical contract and trust-boundary gaps remain.",
    )
    add_callout(
        doc,
        "Bottom line",
        "Keep the modular-monolith direction. Freeze feature expansion, repair the API contracts, remove committed secrets and debug surfaces, "
        "then prove the payment/ledger invariants with the full test suite before any public or real-money deployment.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )
    doc.add_paragraph("Reviewer assessment", style="Heading 2")
    for label, score, color in [
        ("Functional breadth", 8, GREEN),
        ("Architecture coherence", 6, BLUE),
        ("Test confidence", 6, BLUE),
        ("Operability", 4, ORANGE),
        ("Financial-control readiness", 4, ORANGE),
        ("Security readiness", 3, RED),
        ("Documentation accuracy", 3, RED),
    ]:
        add_score_bar(doc, label, score, color)

    doc.add_paragraph("What is already working", style="Heading 2")
    add_bullet(doc, numbering, "Backend compilation passes and the production frontend bundle builds successfully on the reviewed workspace.")
    add_bullet(doc, numbering, "The domain is no longer a toy CRUD app: it includes catalog, inventory, orders, payment intents, wallet/escrow, payout, returns, revenue, notifications, ecommerce channels, and identity synchronization.")
    add_bullet(doc, numbering, "The codebase contains substantial test investment—175 backend test methods—with dedicated security, concurrency, migration, ledger, payout, and integration coverage.")

    doc.add_paragraph("Immediate release blockers", style="Heading 2")
    add_bullet(doc, numbering, "WooCommerce plugins call `/integrations/woocommerce/v1/...`; the backend and checked-in OpenAPI expose `/integrations/ecommerce/v1/...`.")
    add_bullet(doc, numbering, "The frontend cart client calls `/api/carts/...`, but no current `CartResource` exists in backend source or OpenAPI.")
    add_bullet(doc, numbering, "Credential-shaped values remain committed; debug/error endpoints expose internals; the AI service logs its model API key and returns raw tracebacks.")

    page_break(doc)

    # Reading map and evidence
    add_section_title(doc, "How to read this report", "Scope, method, and evidence quality")
    add_body(
        doc,
        "This is a static architecture review of the tracked workspace, not a penetration test or production load test. "
        "Generated SDK files, compiled bytecode, logs, crash dumps, binary installers, and media were inventoried but not treated as authored design authority.",
    )
    add_table(
        doc,
        ["Evidence stream", "Reviewed material", "Use in assessment"],
        [
            ("Source structure", "141 Java; 117 TSX; 45 TS; 63 SQL; 48 Python; 11 PHP", "Component boundaries, coupling, ownership"),
            ("Runtime/config", "Gradle, Quarkus properties, Docker Compose, Keycloak realm, Vite/npm", "Ports, dependencies, trust boundaries"),
            ("Contracts", "147 extracted REST operations; 134 OpenAPI operations; generated SDK", "API drift and client alignment"),
            ("Verification", "175 @Test methods; CI workflow; local compile/build", "Confidence and delivery gaps"),
            ("Documentation", "12 Markdown files, including 1,179-line branch audit", "Intent, drift, historical findings"),
        ],
        [1900, 3600, 3860],
        first_col_bold=True,
    )
    doc.add_paragraph("Verification performed during this review", style="Heading 2")
    add_bullet(doc, numbering, "`.\\gradlew.bat compileJava` — passed; tasks were up to date.")
    add_bullet(doc, numbering, "`frontend\\npm run build` — passed; TypeScript and Vite production build completed.")
    add_bullet(doc, numbering, "Full backend tests were not executed because Docker Desktop was unavailable; the test configuration requires MariaDB and Keycloak.")
    add_callout(
        doc,
        "Interpretation rule",
        "Facts are stated as repository observations. Scores and recommendations are explicitly reviewer judgments. "
        "The prior 22 July audit is treated as historical evidence and re-checked against current code before reuse.",
    )
    doc.add_paragraph("Reading map", style="Heading 2")
    add_number(doc, numbering, "Architecture and runtime: pages covering context, deployment, backend, frontend, and data.")
    add_number(doc, numbering, "Critical journeys: commerce, payment/escrow, WooCommerce, and AI assistant.")
    add_number(doc, numbering, "Assurance: tenant/security, tests, documentation drift, risk register, and remediation roadmap.")

    page_break(doc)

    # Repository anatomy
    add_section_title(doc, "Repository anatomy", "A single workspace containing five deployable/runtime concerns")
    add_table(
        doc,
        ["Subsystem", "Primary technologies", "Scale observed", "Architectural role"],
        [
            ("Core backend", "Java 24 target; Quarkus 3.33.1.1; Panache; JAX-RS", "110 main Java files; 15.6k lines", "Modular monolith and system of record"),
            ("Main frontend", "React 19.2; TypeScript 5.9; Vite 7; TanStack; Base UI", "38 pages; 47 route files", "Staff, owner, admin, and customer experiences"),
            ("Data layer", "MariaDB 11.4; Flyway", "63 migrations; 40 table-create statements", "Transactional state, history, configuration"),
            ("WooCommerce bridge", "WordPress 7; WooCommerce 10.9.4; PHP", "2 plugins; 1.4k PHP lines", "Storefront/catalog/order integration"),
            ("AI assistant", "FastAPI; Gemini; Pinecone", "13 production Python files", "Product retrieval, store stats, order-status assistant"),
            ("Mock banking", "Node/Express", "Standalone service on :3000", "Development payment simulation"),
        ],
        [1650, 2700, 1850, 3160],
        first_col_bold=True,
        font_size=8.8,
    )
    doc.add_paragraph("Authored vs. generated weight", style="Heading 2")
    add_body(
        doc,
        "The repository reports about 75k lines across major text formats, but approximately 11k lines belong to the generated hey-api client. "
        "The largest authored complexity centers are the frontend pages (about 17.5k lines), Quarkus backend (about 15.6k lines), and backend tests (about 5k lines).",
    )
    add_callout(
        doc,
        "Structural observation",
        "The project name says “lite,” but its current feature surface is a compact commerce platform with financial workflows—not a basic ERP starter. "
        "The architecture should now be governed as a payment-adjacent commerce system.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )
    doc.add_paragraph("Repository hygiene signals", style="Heading 2")
    add_bullet(doc, numbering, "Tracked crash dumps, replay logs, API-error logs, test outputs, compiled Python bytecode, media, and a Python installer increase noise and leakage risk.")
    add_bullet(doc, numbering, "The root README is only a pointer; operational truth is fragmented across AGENTS.md, runGuild.md, showcase guidance, handoffs, and design plans.")
    add_bullet(doc, numbering, "Generated SDK/OpenAPI artifacts coexist with hand-written request wrappers, making contract ownership ambiguous.")

    page_break(doc)

    # System context
    add_section_title(doc, "Architecture context", "The Quarkus application is the system of record and orchestration hub")
    add_figure(
        doc,
        context_img,
        "Figure 1. System context reconstructed from source, runtime configuration, and integration adapters.",
        "System context diagram showing users, React and Quarkus V-ERP core, Keycloak, WooCommerce, AI services, and MariaDB data plane.",
    )
    add_body(
        doc,
        "The strongest macro-architecture choice is the modular monolith: identity, tenant administration, catalog, orders, payments, wallets, and reporting share a single transactional boundary. "
        "For the current team and “lite” scope, that is lower-risk than premature microservices.",
    )
    add_table(
        doc,
        ["Boundary", "Direction", "Current contract"],
        [
            ("Browser ↔ Quarkus", "Bidirectional REST", "OIDC bearer token; generated and hand-written clients"),
            ("Quarkus ↔ Keycloak", "Token validation + admin API", "Realm roles, organizations, custom tenant claim"),
            ("V-ERP → WooCommerce", "Catalog projection", "Sales channel + external entity mappings"),
            ("WooCommerce → V-ERP", "Order/status integration", "HMAC/idempotency intent, but route contract is mismatched"),
            ("AI → Quarkus", "Read-only support calls", "Static API key and caller-supplied tenant header"),
            ("Mock bank → Quarkus", "Payment webhook", "HMAC filter + replay log; mock-specific keying"),
        ],
        [2200, 1900, 5260],
        first_col_bold=True,
        font_size=8.9,
    )

    page_break(doc)

    # Runtime topology
    add_section_title(doc, "Runtime topology", "Local development spans six processes and two databases")
    add_figure(
        doc,
        runtime_img,
        "Figure 2. Current local deployment topology. The checked-in frontend environment calls the backend directly on :8081.",
        "Runtime topology diagram showing browser, Quarkus, Keycloak, MariaDB, WordPress, AI assistant, and mock bank with local ports.",
    )
    add_table(
        doc,
        ["Service", "Port", "Lifecycle / note"],
        [
            ("React/Vite", "5174", "Direct backend base URL is set in `frontend/.env`; Vite proxy is bypassed."),
            ("Quarkus", "8081", "Root path `/api/`; CORS allows all origins."),
            ("Keycloak", "8181", "Dev mode, realm auto-import, shared MariaDB server."),
            ("ERP MariaDB", "3307", "Database `v-erp-lite`; Flyway migrate/repair in dev."),
            ("WordPress/Woo", "8090", "Dedicated database and persistent WordPress volume."),
            ("AI assistant", "8000", "FastAPI started separately; not in Docker Compose."),
            ("Mock bank", "3000", "Standalone Node service; not in Docker Compose."),
        ],
        [2100, 900, 6360],
        first_col_bold=True,
        font_size=8.9,
        alignments=[WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT],
    )
    add_callout(
        doc,
        "Operational implication",
        "The documented startup order covers infrastructure, backend, and frontend, but a full showcase also needs WordPress setup, the AI service, and possibly the mock bank. "
        "There is no single health-checked launcher for the complete demo estate.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )

    page_break(doc)

    # Backend architecture
    add_section_title(doc, "Backend architecture", "A flat package modular monolith with growing service orchestration")
    add_table(
        doc,
        ["Layer", "Observed contents", "Assessment"],
        [
            ("REST", "39 resource/mapper types; 147 extracted operations", "Broad API surface; several resources are large and mix transport with orchestration."),
            ("Services", "31 services; 144 public methods; schedulers for escrow, owner SLA, payout", "Clearer domain orchestration than the original CRUD baseline."),
            ("Entities", "36 entity/model classes; 332 private fields; 28 JPA relationships", "Rich model, but many states remain free-form strings."),
            ("Security", "CallerContext, BusinessAccess, PaymentAccess, OIDC, HMAC filter", "Good central seams; fail-open fallbacks and role breadth weaken them."),
            ("Persistence", "Panache active record + transactions + locks", "Pragmatic, but query discipline is the tenant boundary."),
        ],
        [1550, 4100, 3710],
        first_col_bold=True,
        font_size=8.9,
    )
    doc.add_paragraph("Complexity hotspots", style="Heading 2")
    add_bullet(doc, numbering, "`KeycloakSyncService` is about 736 lines and coordinates realm profiles, organizations, users, roles, passwords, and memberships.")
    add_bullet(doc, numbering, "`ReturnRefundService` is about 707 lines; `WalletService` about 586; `EcommerceOrderService` about 563.")
    add_bullet(doc, numbering, "`OrderResource` exceeds 1,300 lines and carries multiple generations of order state vocabulary and transition logic.")
    add_callout(
        doc,
        "Recommended boundary",
        "Preserve one deployable backend, but split the largest resources/services into internal domain modules: identity, catalog, order lifecycle, payments/ledger, ecommerce adapters, and reporting. "
        "Each module should own its state machine and application service API.",
    )
    doc.add_paragraph("Scheduled automation", style="Heading 2")
    add_table(
        doc,
        ["Job", "Purpose", "Configuration"],
        [
            ("EscrowReleaseScheduler", "Release due escrow and complete held orders", "Scheduler extension; due-time query"),
            ("OwnerDecisionScheduler", "Auto-decline when owner SLA expires", "`orders.owner-decision-*`"),
            ("PayoutScheduler", "Evaluate vendor wallets for automated payout", "`payout.schedule.*` and risk thresholds"),
        ],
        [2200, 3900, 3260],
        first_col_bold=True,
        font_size=9.0,
    )

    page_break(doc)

    # Domain model
    add_section_title(doc, "Domain model", "The data model spans administration, commerce, settlement, and integration")
    add_table(
        doc,
        ["Domain cluster", "Representative entities", "Key design characteristic"],
        [
            ("Identity & tenancy", "Tenant, User, Role, Business, TenantConfig, UserSettings", "Manual tenant column filtering; Keycloak-linked user identity"),
            ("CRM & catalog", "Customer, Category, Product, Variant, Tag, Voucher", "Tenant/business ownership; minor-unit pricing; approval and inventory"),
            ("Order lifecycle", "Order, OrderItem, OrderHistory, CustomerVoucher", "Snapshot-heavy aggregate with soft delete and history"),
            ("Payments", "PaymentIntent, Transaction, Refund, Idempotency, WebhookEvent", "Separate provider intent, event, and replay records"),
            ("Wallet & settlement", "Wallet, WalletTransaction, EscrowRecord, PayoutRequest, Checkpoint", "Available/pending balances and seller escrow"),
            ("Integrations", "SalesChannel, ExternalEntityMapping, InboundEventLog", "Channel partitioning, external IDs, replay prevention"),
            ("Operations", "Notification, RevenueAdjustment, VendorRiskEvent, Bank", "Operational dashboards, payout screening, reporting"),
        ],
        [1750, 3990, 3620],
        first_col_bold=True,
        font_size=8.7,
    )
    doc.add_paragraph("State-model concern", style="Heading 2")
    add_body(
        doc,
        "Core workflows use string states rather than typed enums or dedicated state-machine objects. "
        "The order model contains both the newer sequence (`PENDING_PAYMENT → PAID_ESCROW → OWNER_ACCEPTED → ...`) and legacy values (`PENDING`, `CONFIRMED`, `SHIPPING`, `SHIPPED`). "
        "Transition logic is duplicated in `OrderResource` and `EcommerceOrderService`.",
    )
    add_callout(
        doc,
        "Architectural debt",
        "State vocabulary is now a cross-cutting contract shared by database rows, Java services, REST clients, WooCommerce mappings, and UI conditionals. "
        "Promote it to one canonical transition policy before adding more return, cancellation, or payout states.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )
    doc.add_paragraph("Entity serialization", style="Heading 2")
    add_bullet(doc, numbering, "Several endpoints return entities directly, while payment, wallet, ecommerce, and chatbot areas introduce ad-hoc DTOs and nested response classes.")
    add_bullet(doc, numbering, "The mixed approach increases the chance of exposing internal fields, lazy relationships, MFA/identity values, cost snapshots, or bank details.")
    add_bullet(doc, numbering, "A deliberate API DTO boundary is now justified even if MapStruct remains out of scope.")

    page_break(doc)

    # API surface
    add_section_title(doc, "API surface", "Broad coverage, but three contract authorities compete")
    add_table(
        doc,
        ["API group", "Approx. surface", "Primary consumers"],
        [
            ("Administration", "Tenants, users, roles, businesses, settings, sync", "Admin/staff SPA"),
            ("Commerce", "Products, categories, tags, vouchers, customers, orders", "Staff and customer SPA"),
            ("Financial", "Payment intents, webhooks, wallets, payouts, revenue, VietQR", "SPA, mock bank, schedulers"),
            ("Ecommerce integration", "Health, catalog changes/mappings, external orders/status", "Woo plugins + staff integration UI"),
            ("Operations", "Notifications, i18n, images, health, debug, chatbot", "SPA and local support tooling"),
        ],
        [2200, 3550, 3610],
        first_col_bold=True,
        font_size=8.9,
    )
    doc.add_paragraph("Contract drift found in current source", style="Heading 2")
    add_table(
        doc,
        ["Mismatch", "Evidence", "Impact"],
        [
            ("Woo route prefix", "Plugins use `/woocommerce/v1`; backend/OpenAPI use `/ecommerce/v1`", "Catalog/order connector calls 404"),
            ("Cart API", "Frontend calls `/carts`; no backend resource/OpenAPI path", "Cart, shop, and checkout flows cannot complete against this source"),
            ("OpenAPI freshness", "134 operations in spec vs. 147 extracted resource operations", "Generated SDK is incomplete or stale"),
            ("Operation IDs", "`getStats` and `getOrderStatus` each appear twice", "Generated names acquire numeric suffixes and drift"),
            ("Business routes", "Both `/business` and `/businesses` frontend routes exist", "Navigation ambiguity and stale links"),
        ],
        [1900, 4400, 3060],
        first_col_bold=True,
        font_size=8.65,
    )
    add_callout(
        doc,
        "Contract ownership recommendation",
        "Make the running Quarkus OpenAPI document the only source of truth. Regenerate the SDK in CI, compile all frontend consumers, and run connector contract tests against the exact route prefix.",
    )

    page_break(doc)

    # Frontend
    add_section_title(doc, "Frontend architecture", "Modern platform choices; page components are becoming monoliths")
    add_table(
        doc,
        ["Concern", "Current implementation", "Assessment"],
        [
            ("Routing", "TanStack file routes with generated route tree and code splitting", "Strong fit; 47 route files keep URL ownership visible"),
            ("Server state", "TanStack Query + generated hey-api hooks", "Good default, weakened by manual fetch wrappers"),
            ("Authentication", "oidc-client-ts PKCE; Zustand mirror; proactive refresh", "Thoughtful refresh coalescing; tokens remain in localStorage"),
            ("UI system", "Base UI 1.5, Tailwind v4, shared components", "Consistent direction; avoids Radix duplication"),
            ("Localization", "i18next backend with en/vi namespaces and ETags", "Well integrated across navigation/settings"),
            ("Theme", "Zustand + system preference listener + CSS variables", "Functional, but documentation says Zustand was removed"),
        ],
        [1800, 4200, 3360],
        first_col_bold=True,
        font_size=8.8,
    )
    doc.add_paragraph("Page-level complexity", style="Heading 2")
    add_table(
        doc,
        ["Page", "Lines", "Signal"],
        [
            ("order-detail-page.tsx", "1,871", "13 hand-written fetch calls; returns/cancel/refund/escrow UI in one file"),
            ("cart-checkout-page.tsx", "1,423", "Large checkout orchestration and form surface"),
            ("cart-page.tsx", "1,144", "Depends on missing `/carts` backend contract"),
            ("shop-page.tsx", "1,110", "Storefront, catalog, wallet/cart interactions"),
            ("personal-info-page.tsx", "851", "Profile, customer, wallet, and upload concerns"),
        ],
        [3250, 900, 5210],
        first_col_bold=True,
        font_size=8.8,
        alignments=[WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT],
    )
    add_callout(
        doc,
        "Frontend refactoring target",
        "Extract domain hooks and feature components, not generic component factories. Begin with order-detail and cart/checkout because those pages duplicate the most security-sensitive request and state logic.",
    )

    page_break(doc)

    # Primary journey
    add_section_title(doc, "Primary commerce journey", "The intended experience spans catalog, checkout, owner action, and settlement")
    add_number(doc, numbering, "Catalog publication. Staff creates products, variants, prices, approval state, and inventory snapshots.")
    add_number(doc, numbering, "Customer discovery. The customer SPA or WooCommerce storefront displays approved, active products.")
    add_number(doc, numbering, "Checkout. The backend must reprice, validate voucher/stock, reserve inventory, and create one vendor-scoped order.")
    add_number(doc, numbering, "Payment. A payment intent is created with an idempotency key; a gateway or wallet produces a success event.")
    add_number(doc, numbering, "Escrow. Platform and seller wallet entries are created; seller funds remain held pending fulfillment.")
    add_number(doc, numbering, "Owner decision. The business owner accepts or declines within the configured SLA; a scheduler can auto-decline.")
    add_number(doc, numbering, "Fulfillment and completion. Delivery starts a hold period, then escrow releases or a return/cancel workflow refunds it.")
    doc.add_paragraph("Journey strengths", style="Heading 2")
    add_bullet(doc, numbering, "Order and order-item snapshots protect historical pricing and cost analysis.")
    add_bullet(doc, numbering, "Order history, notifications, idempotency records, inbound event logs, and external mappings create useful audit seams.")
    add_bullet(doc, numbering, "Recent migrations add VND enforcement, business assignments, cost snapshots, recipient-aware notifications, and order-history sequencing.")
    doc.add_paragraph("Journey breaks in this checkout", style="Heading 2")
    add_bullet(doc, numbering, "Native cart routes exist in the frontend and database migrations, but the backend cart resource/entity layer is absent.")
    add_bullet(doc, numbering, "WooCommerce connectors target a route namespace the backend does not expose.")
    add_bullet(doc, numbering, "Order status vocabulary has legacy and new branches; UI and external status mapping can diverge.")
    add_callout(
        doc,
        "Release acceptance test",
        "A clean environment must demonstrate: publish product → buy through the selected storefront → create exactly one order → complete one payment → owner fulfills → escrow releases once → both storefront and ERP show the same final state.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    page_break(doc)

    # Payment flow
    add_section_title(doc, "Payments, wallet, and escrow", "The structure is promising; the trust model still needs a production adapter")
    add_figure(
        doc,
        payment_img,
        "Figure 3. Intended payment-to-settlement sequence derived from payment, wallet, escrow, fulfillment, and payout services.",
        "Six-step payment and escrow flow from checkout through intent, provider, settlement, fulfillment, and release.",
    )
    doc.add_paragraph("Controls now present", style="Heading 2")
    add_bullet(doc, numbering, "Money is represented in integer minor units with `Money`/`MoneyService`; migration V54 converts remaining USD data and adds VND checks.")
    add_bullet(doc, numbering, "Wallet mutations use transaction boundaries, pessimistic locking, sequence numbers, checkpointing, and dedicated concurrency tests.")
    add_bullet(doc, numbering, "Idempotency records now carry request fingerprints and processing leases; inbound webhook events have a unique gateway/event key.")
    add_bullet(doc, numbering, "Top-up creation remains `PENDING` until a webhook calls `completeTopUp`, an improvement over immediate credit.")
    doc.add_paragraph("Production blockers", style="Heading 2")
    add_bullet(doc, numbering, "The HMAC filter and mock gateway share a hard-coded development secret. Production-mode payload verification deliberately returns false, so the current adapter is not a deployable real-provider integration.")
    add_bullet(doc, numbering, "Gateway refunds return deterministic mock references yet are recorded as completed; bank transfers default to mock mode.")
    add_bullet(doc, numbering, "The ledger remains balance-plus-entry accounting rather than balanced double-entry, limiting independent reconciliation.")

    page_break(doc)

    # WooCommerce
    add_section_title(doc, "WooCommerce integration", "The adapter pattern is correct; the route contract currently breaks it")
    add_table(
        doc,
        ["Component", "Responsibility", "Current state"],
        [
            ("SalesChannel", "Tenant/business/store configuration and secret reference", "Good foundation; no secret value stored in channel row"),
            ("ExternalEntityMapping", "ERP ↔ Woo IDs and sync status", "Unique constraints support safe upsert/resume"),
            ("Catalog connector plugin", "Pull health, changes, summary; upsert Woo products", "Hourly/manual sync and admin UI exist"),
            ("Checkout plugin", "Send Woo order with HMAC/idempotency; show status", "Order and status handlers exist"),
            ("Ecommerce resources", "Health, catalog, order, reconcile/status", "Backend namespace is `/integrations/ecommerce/v1`"),
        ],
        [2200, 4100, 3060],
        first_col_bold=True,
        font_size=8.8,
    )
    add_callout(
        doc,
        "Critical mismatch",
        "Both WordPress plugins construct `/integrations/woocommerce/v1/...` URLs, while every backend resource, checked-in OpenAPI path, generated SDK function, and frontend integration wrapper uses `/integrations/ecommerce/v1/...`.",
        fill=PALE_RED,
        label_color=RED,
    )
    doc.add_paragraph("Recommended connector contract", style="Heading 2")
    add_number(doc, numbering, "Choose one canonical prefix—prefer the provider-neutral `/integrations/ecommerce/v1` already implemented.")
    add_number(doc, numbering, "Expose channel identity explicitly; do not overload `businessId` with a channel identifier.")
    add_number(doc, numbering, "Sign the exact raw body plus timestamp/channel context; reject stale timestamps and bind every event to one channel.")
    add_number(doc, numbering, "Add a Docker-backed contract test that activates both plugins and proves catalog, order, status, retry, replay, and restart behavior.")
    add_body(
        doc,
        "The design documents correctly call for V-ERP to remain the source of truth for price, stock, and operational order state. "
        "That direction should be preserved while repairing the route and identity contracts.",
    )

    page_break(doc)

    # AI assistant
    add_section_title(doc, "AI commerce assistant", "Useful prototype, but outside the main security and API architecture")
    add_table(
        doc,
        ["Capability", "Implementation", "Readiness"],
        [
            ("Intent routing", "Gemini classifies order ops, store stats, or retrieval", "Prototype; no deterministic guardrail or evaluation suite"),
            ("Product retrieval", "Gemini embeddings + Pinecone top-k", "Functional concept; full destructive re-index job"),
            ("Order lookup", "Tool calls Quarkus `/chatbot/orders/{number}`", "Returns strict DTO, but uses static API key and fixed tenant"),
            ("Store stats", "Tool calls Quarkus `/chatbot/stats`", "Caller supplies tenant header; not user-bound"),
            ("Chat UI", "React widget calls `http://localhost:8000/chat`", "Hard-coded URL; renders response with `dangerouslySetInnerHTML`"),
            ("FastAPI service", "Global CORS; raw HTML responses", "No authentication, rate limit, or tenant/user session"),
        ],
        [1900, 4150, 3310],
        first_col_bold=True,
        font_size=8.7,
    )
    doc.add_paragraph("Confirmed high-risk behaviors", style="Heading 2")
    add_bullet(doc, numbering, "The FastAPI server prints the model API key at startup.")
    add_bullet(doc, numbering, "Unhandled errors are returned to the browser as raw tracebacks.")
    add_bullet(doc, numbering, "The UI renders model output as HTML, and the server interpolates model/traceback content into HTML without sanitization.")
    add_bullet(doc, numbering, "The sync job embeds direct database credentials and queries MariaDB outside the Quarkus authorization boundary.")
    add_callout(
        doc,
        "Recommended containment",
        "Treat the AI assistant as an experimental sidecar. Put it behind Quarkus, propagate the authenticated user and tenant server-side, expose allow-listed DTO tools, sanitize Markdown, and remove direct database access before enabling it beyond local demos.",
        fill=PALE_RED,
        label_color=RED,
    )

    page_break(doc)

    # Data architecture
    add_section_title(doc, "Data architecture and migrations", "A rich schema with improving constraints and a long evolutionary tail")
    add_table(
        doc,
        ["Measure", "Observed value", "Interpretation"],
        [
            ("Flyway files", "63 migrations, V1 through V58 with interleaved versions", "Active evolution; migration ordering must remain disciplined"),
            ("Tables created", "40 create-table statements", "More tables than JPA entities because carts/junctions/history coexist"),
            ("Foreign-key references", "84 occurrences", "Good relational foundation"),
            ("Unique controls", "34 occurrences", "Useful for idempotency, mappings, identity, and deduplication"),
            ("Check constraints", "27 occurrences", "Recent VND and financial-domain hardening"),
            ("Indexes", "42 create-index statements", "Coverage added over time for ledger, orders, mappings, events"),
        ],
        [2350, 2200, 4810],
        first_col_bold=True,
        font_size=9.0,
    )
    doc.add_paragraph("Evolution pattern", style="Heading 2")
    add_body(
        doc,
        "The schema began with tenant/user/customer/business CRUD, then added settings, catalog, variants, orders, vouchers, payments, wallets, ecommerce channels, payouts, notifications, returns, VND enforcement, and single-owner consolidation. "
        "Later repair migrations (for missing tables, currency completion, assignment repair, and escrow migration) show the cost of parallel feature development.",
    )
    doc.add_paragraph("Important strengths", style="Heading 2")
    add_bullet(doc, numbering, "Composite tenant/channel/business foreign keys in ecommerce migrations reduce cross-tenant mapping errors.")
    add_bullet(doc, numbering, "External mappings and inbound events use uniqueness constraints rather than application-only deduplication.")
    add_bullet(doc, numbering, "Version columns, soft deletion, history sequence numbers, and inventory cost snapshots support safer concurrent updates and reporting.")
    doc.add_paragraph("Data-governance gaps", style="Heading 2")
    add_bullet(doc, numbering, "Database credentials and admin secrets are still checked into development configuration and the Keycloak realm export.")
    add_bullet(doc, numbering, "PII, MFA secrets, identity-provider IDs, bank account numbers, and raw event/error data do not have an explicit retention or masking policy.")
    add_bullet(doc, numbering, "Carts and cart items exist in migrations but have no current backend entity/resource implementation, leaving orphaned schema capability.")

    page_break(doc)

    # Tenant/security
    add_section_title(doc, "Tenant isolation and authorization", "Centralized seams exist, but fallbacks undermine fail-closed behavior")
    add_figure(
        doc,
        tenant_img,
        "Figure 4. Current tenant resolution and the recommended fail-closed decision path.",
        "Tenant authorization diagram showing OIDC token, CallerContext, scoped queries, failure modes, and target fail-closed posture.",
    )
    add_table(
        doc,
        ["Control", "Positive design", "Current weakness"],
        [
            ("Tenant resolution", "CallerContext centralizes claim parsing", "Falls back to `azp`, then literal `default`"),
            ("User resolution", "Email + tenant lookup; inactive users blocked", "Auto-provisioning expands side effects during reads"),
            ("Business scope", "BusinessAccess helper and assignment field", "Operator/owner fallback can select owner or first tenant business"),
            ("Resource roles", "Extensive `@RolesAllowed` use", "Broad role lists; debug/chatbot/image endpoints lack normal OIDC policy"),
            ("Tenant queries", "Many ID lookups include tenantId", "Manual discipline remains the only isolation mechanism"),
        ],
        [2000, 3550, 3810],
        first_col_bold=True,
        font_size=8.8,
    )
    add_callout(
        doc,
        "Target invariant",
        "Every protected request must resolve exactly one authenticated tenant and, where relevant, one assigned business. "
        "Missing or conflicting claims must yield 403—not `azp`, `default`, owner-email, or first-row fallback.",
        fill=PALE_RED,
        label_color=RED,
    )
    doc.add_paragraph("Identity-provider concerns", style="Heading 2")
    add_bullet(doc, numbering, "The realm export contains credential-shaped identity-provider and admin-client secrets.")
    add_bullet(doc, numbering, "Keycloak user synchronization assigns the same known permanent development password to newly created users.")
    add_bullet(doc, numbering, "The public SPA client still enables direct access grants, expanding the token endpoint attack surface beyond PKCE browser login.")

    page_break(doc)

    # Security
    add_section_title(doc, "Security and privacy posture", "Development conveniences are still reachable in the main application")
    add_table(
        doc,
        ["Finding", "Evidence in current source", "Priority"],
        [
            ("Committed credentials", "Application properties, realm export, chatbot code, mock services", "P0 — rotate and remove"),
            ("Debug exposure", "`/debug-db/*`, test-error route, global exception details", "P0 — remove/profile-gate"),
            ("AI secret/data leakage", "API-key logging, raw traceback, HTML injection surface", "P0 — contain sidecar"),
            ("Anonymous file upload", "`@PermitAll`; local disk persistence; no quota/scan/auth", "P1 — protect and externalize"),
            ("Overbroad CORS", "Quarkus `*`; FastAPI `*` with credentials", "P1 — origin allow-list"),
            ("Sensitive API shapes", "Entities and wallet responses include internal/bank fields", "P1 — DTO + masking"),
            ("Token storage", "Access and refresh tokens mirrored to localStorage", "P1 — XSS-sensitive design"),
        ],
        [2200, 4500, 2660],
        first_col_bold=True,
        font_size=8.55,
    )
    doc.add_paragraph("Security strengths to preserve", style="Heading 2")
    add_bullet(doc, numbering, "OIDC authorization-code flow with PKCE S256 and a centralized 401 refresh mechanism.")
    add_bullet(doc, numbering, "HMAC request-filter seam, constant-time signature comparison, request replay logging, and server-side ecommerce secret references.")
    add_bullet(doc, numbering, "Tenant-aware lookup patterns and dedicated payment/business access helpers.")
    add_callout(
        doc,
        "Important distinction",
        "The code contains many security mechanisms, but mechanisms do not equal a secure posture when dev secrets, fallback identity resolution, debug endpoints, and unauthenticated sidecars bypass them.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )

    page_break(doc)

    # Quality and CI
    add_section_title(doc, "Testing and delivery controls", "Strong backend test growth; weak enforcement in CI")
    add_table(
        doc,
        ["Test area", "Observed coverage", "Confidence signal"],
        [
            ("Money and invariants", "MoneyService, Money model, ledger/payment invariants", "Good unit-level arithmetic coverage"),
            ("Concurrency", "Wallet concurrency and payment load tests", "Useful race-condition focus"),
            ("Security/tenant", "29 PaymentSecurity tests + tenant isolation/business access", "Unusually strong for an intern project"),
            ("Payments/payouts", "Intent, webhook, payout, settlement, pending-balance tests", "Meaningful regression safety"),
            ("Ecommerce", "Integration service, order service, migrations", "Good backend foundation; no plugin E2E"),
            ("Frontend", "No test runner", "Build catches types/bundling only"),
        ],
        [2200, 4000, 3160],
        first_col_bold=True,
        font_size=8.8,
    )
    doc.add_paragraph("CI gap", style="Heading 2")
    add_body(
        doc,
        "The GitHub workflow starts the full Docker Compose stack but executes only four selected backend test classes. "
        "It does not run all 175 tests, the frontend build, OpenAPI regeneration/diff, PHP checks, plugin activation, or an end-to-end storefront flow.",
    )
    add_code(
        doc,
        'CI currently: ./gradlew test --tests "WalletServiceTest" --tests "PaymentTenantIsolationTest"\n'
        '              --tests "PaymentModuleInvariantTest" --tests "MigrationValidationTest"',
    )
    doc.add_paragraph("Recommended verification pyramid", style="Heading 2")
    add_number(doc, numbering, "Fast gate: backend compile, all unit tests, frontend typecheck/build, generated-contract diff.")
    add_number(doc, numbering, "Database gate: clean Flyway migration, upgrade-from-supported-baseline, constraints/invariant tests.")
    add_number(doc, numbering, "Integration gate: Keycloak login/roles, Woo plugin activation and contract tests, signed webhook replay tests.")
    add_number(doc, numbering, "Journey gate: one full catalog → checkout → payment → fulfillment → escrow/refund scenario.")
    add_callout(
        doc,
        "Current verification result",
        "Backend compilation and frontend production build passed during this review. Full backend tests were not run because Docker Desktop was not available on the workstation.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    page_break(doc)

    # Documentation drift
    add_section_title(doc, "Documentation integrity", "Project guidance describes an older, smaller system")
    add_table(
        doc,
        ["Topic", "Guidance says", "Current repository says"],
        [
            ("Database", "PostgreSQL on 5433", "MariaDB 11.4 on 3307"),
            ("Tests", "Test directory is empty", "31 test source files; 175 test methods"),
            ("Migrations", "V1–V8 summary", "63 migrations through V58"),
            ("Frontend state", "Zustand removed / avoid it", "Zustand 5 is installed and used for auth/theme"),
            ("Java", "JDK 25", "Gradle home JDK 25; source/target compatibility 24"),
            ("Architecture", "Tenant/user/customer/business CRUD", "Commerce, payments, wallet, payout, Woo, AI, mock bank"),
            ("DTO policy", "No DTOs", "DTO records/classes and nested API response types now exist"),
        ],
        [1750, 3440, 4170],
        first_col_bold=True,
        font_size=8.75,
    )
    doc.add_paragraph("Existing engineering audit", style="Heading 2")
    add_body(
        doc,
        "The 22 July branch audit is extensive: 1,179 lines with 9 P0, 31 P1, 14 P2, and 1 P3 headings. "
        "Subsequent migrations and tests appear to address parts of its currency, top-up, idempotency, replay, business-assignment, and escrow concerns. "
        "Many findings—committed secrets, known user password, fallbacks, debug surfaces, manual frontend requests, partial CI, OpenAPI collisions, and route drift—remain visible.",
    )
    add_callout(
        doc,
        "Documentation action",
        "Replace the root README and AGENTS.md architecture snapshot with a generated, current system overview. "
        "Archive design plans as historical ADR inputs, and maintain one release checklist tied to CI.",
    )
    doc.add_paragraph("Source-encoding signal", style="Heading 2")
    add_body(
        doc,
        "Multiple tracked files display mojibake in comments, Markdown, and UI strings under the current shell encoding. "
        "Even when runtime rendering is correct, this complicates review and can leak malformed Vietnamese text into generated artifacts.",
    )

    page_break(doc)

    # Strengths
    add_section_title(doc, "What the engineering team did well", "A solid foundation worth hardening rather than replacing")
    strengths = [
        ("Pragmatic deployment shape", "One Quarkus transactional core is appropriate for team size and domain coupling."),
        ("Explicit tenant discipline", "The team documented and repeatedly applied tenant-aware ID lookups."),
        ("Financial-domain ambition", "Idempotency, escrow, payouts, refunds, cost snapshots, replay logs, and checkpoints are sophisticated learning targets."),
        ("Test investment", "Security, concurrency, migration, and financial regression tests are much stronger than the stale documentation suggests."),
        ("Modern frontend", "TanStack Router/Query, generated SDK, Base UI, Tailwind v4, OIDC PKCE, i18n, and theme support form a coherent stack."),
        ("Integration foundations", "Sales channels, server-side secret references, external mappings, and connector-specific plugins are the right abstractions."),
        ("Operational storytelling", "Showcase and handoff guides explain visible outcomes, recovery paths, and release gates."),
    ]
    for title, detail in strengths:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(9)
        p.paragraph_format.keep_together = True
        r1 = p.add_run(title + "  ")
        set_run_font(r1, size=11, color=GREEN, bold=True)
        r2 = p.add_run(detail)
        set_run_font(r2, size=11, color=INK)
    add_callout(
        doc,
        "Architect’s view",
        "The repository does not need a rewrite. It needs convergence: one contract, one state vocabulary, one tenant decision path, one production payment adapter boundary, and one enforced verification pipeline.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    page_break(doc)

    # Risk register
    add_section_title(doc, "Priority risk register", "Current blockers and near-term engineering risks")
    add_table(
        doc,
        ["ID", "Risk", "Impact", "Likelihood", "Owner / first move"],
        [
            ("R1", "Credential-shaped secrets are committed", "Identity/provider compromise", "High", "Platform — rotate, purge, externalize"),
            ("R2", "Woo route prefix mismatch", "Connector is nonfunctional", "Certain", "Integration — align + contract test"),
            ("R3", "Frontend cart has no backend API", "Native checkout fails", "Certain", "Commerce — restore or remove cart flow"),
            ("R4", "Debug/exception endpoints expose internals", "Data leakage and attack aid", "High", "Backend — delete or dev-profile gate"),
            ("R5", "AI service is unauthenticated and XSS-prone", "Tenant/data leakage; code injection", "High", "AI — proxy, sanitize, authenticate"),
            ("R6", "Tenant/business resolution falls back", "Cross-business access", "Medium–High", "Security — fail closed"),
            ("R7", "Production payment adapter is mock-only", "Payments fail or are unauditable", "Certain", "Payments — provider interface + secrets"),
            ("R8", "Partial CI leaves most tests unenforced", "Regression reaches develop/main", "High", "DevOps — run full gates"),
        ],
        [600, 2600, 2300, 1300, 2560],
        first_col_bold=True,
        font_size=8.35,
        alignments=[
            WD_ALIGN_PARAGRAPH.CENTER,
            WD_ALIGN_PARAGRAPH.LEFT,
            WD_ALIGN_PARAGRAPH.LEFT,
            WD_ALIGN_PARAGRAPH.CENTER,
            WD_ALIGN_PARAGRAPH.LEFT,
        ],
    )
    doc.add_paragraph("Secondary risks", style="Heading 2")
    add_bullet(doc, numbering, "Order/payment state transitions are duplicated and stringly typed, increasing invalid-transition risk.")
    add_bullet(doc, numbering, "Manual fetch clients bypass the central 401 refresh, error contract, and generated types.")
    add_bullet(doc, numbering, "Bank/account/identity fields lack masking and retention controls.")
    add_bullet(doc, numbering, "Public local-disk uploads have no quota, malware scanning, ownership, or lifecycle management.")
    add_bullet(doc, numbering, "Demo/runtime dependencies are not composed into one reproducible environment.")
    add_callout(
        doc,
        "Release stance",
        "No-go for internet-facing or real-money production. Conditional go for an internal demo only after secrets are rotated, debug/AI exposure is contained, and the chosen checkout path is proven end to end.",
        fill=PALE_RED,
        label_color=RED,
    )

    page_break(doc)

    # Roadmap 0-30
    add_section_title(doc, "Remediation roadmap", "Phase 0–1: contain risk and restore a coherent contract")
    doc.add_paragraph("First 48 hours — containment", style="Heading 2")
    add_number(doc, numbering, "Rotate every committed credential-shaped value: Keycloak admin client, Google IdP, chatbot key, mock HMAC, database passwords.")
    add_number(doc, numbering, "Remove secrets from tracked files and history where practical; provide `.env.example` placeholders and secret-injection documentation.")
    add_number(doc, numbering, "Delete or `%dev`-gate debug resources, test-error routes, verbose exception details, mock payment endpoints, and model API-key logging.")
    add_number(doc, numbering, "Disable the AI widget outside local development until it is authenticated, proxied, and output-sanitized.")

    doc.add_paragraph("Week 1 — contract convergence", style="Heading 2")
    add_number(doc, numbering, "Standardize on `/integrations/ecommerce/v1`; update both WordPress plugins and all docs.")
    add_number(doc, numbering, "Decide the native cart strategy: implement `CartResource`/entities from existing schema or remove stale cart UI and route to order checkout.")
    add_number(doc, numbering, "Regenerate OpenAPI/SDK; eliminate duplicate operation IDs and all manual frontend fetch calls to core APIs.")
    add_number(doc, numbering, "Remove singular `/business` route and other duplicate route aliases after adding redirects if needed.")

    doc.add_paragraph("Week 2 — trust boundaries", style="Heading 2")
    add_number(doc, numbering, "Make tenant resolution fail closed; require a verified organization/tenant claim and explicit business assignment.")
    add_number(doc, numbering, "Replace broad role checks with policy methods for admin, business owner, operator, customer, and connector identities.")
    add_number(doc, numbering, "Introduce API DTOs for users, customers, products, wallets, bank details, MFA, and ecommerce payloads.")
    add_callout(
        doc,
        "Exit criterion",
        "A clean checkout of the repository must build, migrate, log in, activate plugins, and pass contract tests without editing tracked secrets or source URLs.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    page_break(doc)

    # Roadmap 30-90
    add_section_title(doc, "Remediation roadmap", "Phase 2–4: prove financial correctness and operational readiness")
    doc.add_paragraph("Days 15–30 — financial model", style="Heading 2")
    add_number(doc, numbering, "Define one canonical order state machine and one payment state machine; remove legacy transitions or map them at the edge.")
    add_number(doc, numbering, "Enforce one successful payment intent per order and bind gateway transaction IDs, amount, currency, tenant, and request fingerprint.")
    add_number(doc, numbering, "Separate provider calls from durable accounting with an outbox/saga-style recovery record; never record a mock refund as completed in a production profile.")
    add_number(doc, numbering, "Document whether the wallet ledger is intentionally single-entry. If real money is in scope, move to balanced double-entry journal lines.")

    doc.add_paragraph("Days 31–60 — full verification", style="Heading 2")
    add_number(doc, numbering, "Run all 175 backend tests in CI; add frontend tests for auth refresh, role navigation, cart/checkout, and order state UI.")
    add_number(doc, numbering, "Add clean-install and upgrade migration tests; verify all financial constraints against MariaDB 11.4.")
    add_number(doc, numbering, "Add WooCommerce plugin integration tests and a signed webhook replay/concurrency suite.")
    add_number(doc, numbering, "Add SAST/secret scanning, dependency review, container image scanning, and artifact hygiene checks.")

    doc.add_paragraph("Days 61–90 — operability", style="Heading 2")
    add_number(doc, numbering, "Create environment profiles for local, demo, staging, and production; mock services must be impossible to enable accidentally in production.")
    add_number(doc, numbering, "Add structured audit logging, metrics, distributed correlation IDs, backup/restore tests, data retention, and incident runbooks.")
    add_number(doc, numbering, "Containerize the AI and mock bank only if they remain in scope; otherwise remove them from the production architecture.")
    add_callout(
        doc,
        "Production readiness gate",
        "Independent reviewers should be able to trace every unit of money from provider evidence to balanced accounting, order state, escrow disposition, refund/payout, and immutable audit history.",
        fill=PALE_GOLD,
        label_color=GOLD,
    )

    page_break(doc)

    # Target architecture
    add_section_title(doc, "Target architecture", "A hardened modular monolith with adapter boundaries")
    add_table(
        doc,
        ["Module", "Owns", "May depend on"],
        [
            ("Identity & tenancy", "Tenant/user/business policy, Keycloak sync", "Keycloak adapter, persistence"),
            ("Catalog", "Product/category/tag/variant/price/inventory", "Identity policy, persistence"),
            ("Orders", "Cart/checkout/order lifecycle/history", "Catalog, identity policy"),
            ("Payments", "Intent, provider adapter, idempotency, webhook inbox", "Orders, accounting"),
            ("Accounting", "Wallet journal, escrow, refunds, payouts, reconciliation", "Identity policy, persistence"),
            ("Ecommerce adapter", "Sales channels, mappings, Woo DTOs/status mapping", "Catalog, orders"),
            ("Experience API", "Stable DTOs/OpenAPI for SPA and connectors", "Application services only"),
            ("AI adapter", "Allow-listed support tools and sanitized responses", "Experience API; no direct DB"),
        ],
        [2050, 4300, 3010],
        first_col_bold=True,
        font_size=8.75,
    )
    doc.add_paragraph("Non-negotiable rules", style="Heading 2")
    add_bullet(doc, numbering, "One deployed Quarkus application is acceptable; internal modules must not query each other’s tables ad hoc.")
    add_bullet(doc, numbering, "Every application-service method receives verified tenant and actor context.")
    add_bullet(doc, numbering, "Every external integration terminates at a typed adapter with explicit authentication, idempotency, timeout, retry, and reconciliation semantics.")
    add_bullet(doc, numbering, "Only DTOs cross HTTP boundaries; generated clients are refreshed and diffed in CI.")
    add_bullet(doc, numbering, "Financial state transitions and journal writes are atomic or recoverable by design.")
    add_callout(
        doc,
        "Why this target",
        "It preserves the team’s existing stack and successful patterns while reducing the real risks: duplicate state machines, implicit tenant context, mock-provider leakage, and contract drift.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    page_break(doc)

    # Appendix
    add_section_title(doc, "Appendix", "Key inventory and review references")
    add_table(
        doc,
        ["Inventory item", "Value"],
        [
            ("Tracked files", "540"),
            ("Major text lines", "≈75,206 across Java/TSX/TS/SQL/Python/PHP/Markdown/config"),
            ("Backend main", "110 files; ≈15,593 lines"),
            ("Backend tests", "31 Java source files; 175 @Test methods; ≈5,017 lines"),
            ("Frontend", "38 page components; 47 route files; 17 generated SDK files"),
            ("REST/OpenAPI", "147 extracted resource operations; 134 checked-in OpenAPI operations"),
            ("Persistence", "36 entity/model classes; 63 migrations; 40 create-table statements"),
            ("WooCommerce", "2 plugins; 11 PHP files; 7 WordPress HTTP calls"),
            ("AI assistant", "13 production Python files; FastAPI + Gemini + Pinecone"),
        ],
        [3200, 6160],
        first_col_bold=True,
        font_size=9.0,
    )
    doc.add_paragraph("Primary evidence paths", style="Heading 2")
    for path in [
        "AGENTS.md",
        "build.gradle; gradle.properties; docker-compose.yml",
        "src/main/java/base/{entity,rest,security,service}",
        "src/main/resources/application.properties",
        "src/main/resources/db/migration",
        "frontend/src/{app,pages,routes,shared}; frontend/openapi-spec.yaml",
        "docker/wordpress/wp-content/plugins",
        "ecommerce-chatbot",
        ".github/workflows/payment-ci.yml",
        "develop-branch-audit-2026-07-22.md",
        "SHOWCASE_GUIDE.md and WooCommerce design/handoff documents",
    ]:
        add_bullet(doc, numbering, path)
    add_body(
        doc,
        "This report intentionally omits literal secret values, personal data, raw access tokens, and other credential material observed in the workspace.",
        italic=True,
        color=MUTED,
    )
    add_callout(
        doc,
        "Final assessment",
        "V-ERP Lite is a credible learning and demonstration platform with a surprisingly mature domain surface. "
        "Its next milestone should be engineering convergence and assurance—not additional features.",
        fill=PALE_GREEN,
        label_color=GREEN,
    )

    # Global paragraph behavior and widows/orphans
    for paragraph in doc.paragraphs:
        p_pr = paragraph._p.get_or_add_pPr()
        widow = p_pr.find(qn("w:widowControl"))
        if widow is None:
            widow = OxmlElement("w:widowControl")
            p_pr.append(widow)
        widow.set(qn("w:val"), "true")

    doc.save(OUT_PATH)
    return OUT_PATH


if __name__ == "__main__":
    output = build_document()
    print(output)
