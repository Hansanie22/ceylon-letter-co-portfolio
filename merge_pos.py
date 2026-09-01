import re
import sys

def main():
    try:
        with open("src/main/resources/static/salesrep.html", "r") as f:
            salesrep_content = f.read()

        # Extract POS HTML
        pos_match = re.search(r'<!-- ═══════════════════════ POS ═══════════════════════ -->(.*?)<!-- ═══════════════════════ MY ORDERS ═══════════════════════ -->', salesrep_content, re.DOTALL)
        if not pos_match:
            print("Could not find POS HTML")
            return
        pos_html = pos_match.group(1).replace('<section class="tab-content" id="tab-pos">', '<main id="tab-pos" class="admin-main" style="display:none;">').replace('</section>', '</main>')

        # Extract JS
        js_match = re.search(r'// ─── POS — PRODUCTS ───────────────────────────────────────────────────────────(.*?)(?=// ─── MY ORDERS ─────────────────────────────────────────────────────────────────)', salesrep_content, re.DOTALL)
        if not js_match:
            print("Could not find POS JS")
            return
        pos_js = js_match.group(1)
        
        # We need to replace fetch endpoints in POS JS to remove `${BASE}/` and just use `api/`
        pos_js = pos_js.replace("`${BASE}/api/", "`api/")

        # Extract CSS
        css_match = re.search(r'/\* ── POS SECTION ── \*/(.*?)(?=/\* ── ORDERS TABLE ── \*/)', salesrep_content, re.DOTALL)
        if not css_match:
            print("Could not find POS CSS")
            return
        pos_css = css_match.group(0)

        # Apply to admin.html
        with open("src/main/resources/static/admin.html", "r") as f:
            admin_content = f.read()

        # Insert POS HTML
        insert_html_at = admin_content.find('      <!-- REP REQUESTS TAB -->')
        if insert_html_at == -1:
            print("Could not find insertion point for HTML")
            return
        admin_content = admin_content[:insert_html_at] + "\n      <!-- ═══════════════════════ POS TAB ═══════════════════════ -->\n" + pos_html + "\n" + admin_content[insert_html_at:]

        # Insert JS
        insert_js_at = admin_content.find('  // ── SALES REPS ──────────────────────────────────────────────────────────')
        if insert_js_at == -1:
            print("Could not find insertion point for JS")
            return
        admin_content = admin_content[:insert_js_at] + "\n  // ── POS LOGIC ──\n" + pos_js + "\n" + admin_content[insert_js_at:]
        
        # Insert CSS
        insert_css_at = admin_content.find('    /* ── END ADMIN UI CSS ── */')
        if insert_css_at == -1:
            # try finding another good spot
            insert_css_at = admin_content.find('  </style>')
        if insert_css_at == -1:
            print("Could not find insertion point for CSS")
            return
            
        admin_content = admin_content[:insert_css_at] + "\n" + pos_css + "\n" + admin_content[insert_css_at:]

        with open("src/main/resources/static/admin.html", "w") as f:
            f.write(admin_content)

        print("Merged successfully!")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
