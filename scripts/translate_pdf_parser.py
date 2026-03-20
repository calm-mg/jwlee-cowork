import fitz  # PyMuPDF
import re
import sys
import os

def get_common_font_size(blocks):
    font_sizes = {}
    for block in blocks:
        if 'type' in block and block['type'] == 0:
            for line in block.get("lines", []):
                for span in line.get("spans", []):
                    size = round(span['size'])
                    font_sizes[size] = font_sizes.get(size, 0) + 1
    if not font_sizes:
        return 10.0
    return max(font_sizes, key=font_sizes.get)

def parse_pdf(pdf_path: str, image_output_dir: str = None):
    doc = fitz.open(pdf_path)
    pdf_title = os.path.splitext(os.path.basename(pdf_path))[0]
    if image_output_dir and not os.path.exists(image_output_dir):
        os.makedirs(image_output_dir)

    for page_num in range(len(doc)):
        page = doc.load_page(page_num)
        text_blocks_raw = page.get_text("dict", flags=fitz.TEXTFLAGS_TEXT)["blocks"]
        image_list = page.get_images(full=True)

        all_elements_on_page = []
        for block in text_blocks_raw:
            block['x0'] = block['bbox'][0]
            block['y0'] = block['bbox'][1]
            all_elements_on_page.append(block)

        for img_index, img in enumerate(image_list):
            xref = img[0]
            bbox = page.get_image_bbox(img)
            if bbox:
                base_image = doc.extract_image(xref)
                image_bytes = base_image["image"]
                ext = base_image["ext"]
                image_filename = f"image_{pdf_title}-p{page_num + 1}_{img_index + 1}.{ext}"
                if image_output_dir:
                    with open(os.path.join(image_output_dir, image_filename), "wb") as f:
                        f.write(image_bytes)

                image_elements = {
                    "type": "image_raw",
                    "bbox": bbox,
                    "y0": bbox.y0,
                    "x0": bbox.x0,
                    "image_filename": image_filename
                }
                all_elements_on_page.append(image_elements)
        
        all_elements_on_page.sort(key=lambda b: (b['y0'], b['x0']))
        body_font_size = get_common_font_size([b for b in text_blocks_raw if 'type' in b and b['type'] == 0])
        
        last_image_bbox = None
        for element_raw in all_elements_on_page:
            if element_raw.get("type") == "image_raw":
                yield {'type': 'image', 'content': f"![[{element_raw['image_filename']}]]"}
                last_image_bbox = element_raw["bbox"]
                continue

            if element_raw['type'] == 0:
                full_block_text = ""
                lines = element_raw.get("lines", [])
                if not lines: continue
                first_span_in_block = None
                for line in lines:
                    if line.get("spans"):
                        first_span_in_block = line["spans"][0]
                        break
                if first_span_in_block:
                    span_size = round(first_span_in_block['size'])
                    font_name = first_span_in_block['font'].lower()
                    is_italic = bool(first_span_in_block['flags'] & 1)
                else:
                    span_size = body_font_size
                    font_name = "unknown"
                    is_italic = False

                for line in lines:
                    line_text = "".join([span["text"] for span in line.get("spans", [])])
                    full_block_text += line_text + "\n"
                full_block_text = full_block_text.strip()
                if not full_block_text: continue

                if last_image_bbox and element_raw['bbox'][1] > last_image_bbox[3] and (element_raw['bbox'][1] - last_image_bbox[3]) < 50:
                    if re.match(r'^(Figure|Fig\.|Table)\s*\d+', full_block_text, re.IGNORECASE):
                        yield {'type': 'caption', 'content': f"_{full_block_text}_"}
                        last_image_bbox = None
                        continue
                last_image_bbox = None

                if 'mono' in font_name or 'courier' in font_name or (full_block_text.count('\n') > 1 and len(element_raw['lines'][0]['spans'][0]['text']) > 0 and element_raw['lines'][0]['spans'][0]['origin'][0] - element_raw['bbox'][0] > 20):
                    yield {'type': 'code', 'content': full_block_text}
                    continue

                if re.match(r'^(Chapter|Section)\s\d+', full_block_text) or (span_size > body_font_size * 1.2 and not re.match(r'^[\*\-]?\s*(\d+\.)', full_block_text)):
                    level = 1 if span_size > body_font_size * 1.5 else (2 if span_size > body_font_size * 1.2 else 3)
                    yield {'type': 'heading', 'level': level, 'content': full_block_text}
                    continue
                
                if re.match(r'^[\*\-]\s', full_block_text) or re.match(r'^\d+\.\s', full_block_text):
                    yield {'type': 'list_item', 'content': full_block_text}
                    continue

                if is_italic and len(full_block_text.splitlines()) > 1:
                    yield {'type': 'quote', 'content': full_block_text}
                    continue
                
                yield {'type': 'paragraph', 'content': full_block_text}

def format_element_to_markdown(element: dict) -> str:
    elem_type = element["type"]
    content = element.get("content", "")
    if elem_type == "heading": return f"\n{'#' * element['level']} {content}\n"
    if elem_type == "code": return f"\n```\n{content.strip()}\n```\n"
    if elem_type == "paragraph": return f"{content}\n"
    if elem_type == "list_item": return f"{content}\n"
    if elem_type == "quote":
        quoted_content = "\n> ".join(content.strip().split('\n'))
        return f"\n> {quoted_content}\n"
    if elem_type == "caption": return f"{content}\n"
    if elem_type == "image": return f"\n{content}\n"
    return content

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python translate_pdf_parser.py <pdf_path> [image_output_dir]")
        sys.exit(1)
    pdf_path = sys.argv[1]
    image_output_dir = sys.argv[2] if len(sys.argv) > 2 else None
    
    elements = list(parse_pdf(pdf_path, image_output_dir))
    markdown_elements = [format_element_to_markdown(e) for e in elements]
    full_markdown = "\n".join(markdown_elements)
    full_markdown = re.sub(r'\n{3,}', '\n\n', full_markdown)
    print(full_markdown)
