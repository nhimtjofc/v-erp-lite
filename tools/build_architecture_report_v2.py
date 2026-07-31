"""Pagination refinement for the V-ERP architecture report.

Named design override:
  compact_technical_lists — 6 pt after list items instead of the base
  standard_business_brief 8 pt. This prevents isolated continuation items
  while retaining 1.167 line spacing and the preset's numbering geometry.
"""

from docx.shared import Pt

import build_architecture_report as base


def page_break(doc):
    # Defer the break to the next section kicker. A standalone break paragraph
    # can create an empty page when the previous section ends at the page limit.
    doc._next_section_page_break = True


def add_section_title(doc, kicker, title, subtitle=None):
    kicker_paragraph = base.add_kicker(doc, kicker)
    if getattr(doc, "_next_section_page_break", False):
        kicker_paragraph.paragraph_format.page_break_before = True
        doc._next_section_page_break = False
    heading = doc.add_paragraph(title, style="Heading 1")
    heading.paragraph_format.space_before = Pt(0)
    if subtitle:
        paragraph = doc.add_paragraph()
        paragraph.paragraph_format.space_after = Pt(10)
        paragraph.paragraph_format.keep_with_next = True
        run = paragraph.add_run(subtitle)
        base.set_run_font(run, size=11.5, color=base.MUTED, italic=True)
    return heading


original_add_bullet = base.add_bullet
original_add_number = base.add_number


def add_bullet(doc, numbering, text, bold_prefix=None, color=base.INK):
    paragraph = original_add_bullet(doc, numbering, text, bold_prefix, color)
    paragraph.paragraph_format.space_after = Pt(6)
    return paragraph


def add_number(doc, numbering, text, bold_prefix=None):
    paragraph = original_add_number(doc, numbering, text, bold_prefix)
    paragraph.paragraph_format.space_after = Pt(6)
    return paragraph


base.page_break = page_break
base.add_section_title = add_section_title
base.add_bullet = add_bullet
base.add_number = add_number

if __name__ == "__main__":
    print(base.build_document())
