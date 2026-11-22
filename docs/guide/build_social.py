#!/usr/bin/env python3
"""Build Facebook carousel slides (1080x1350) and square summaries from the guide."""
from pathlib import Path
import html
import subprocess

HERE = Path(__file__).resolve().parent
OUT = HERE / "social"
EN = OUT / "facebook-en"
ES = OUT / "facebook-es"
for d in (EN, ES, OUT / "square"):
    d.mkdir(parents=True, exist_ok=True)

CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { width: 1080px; height: 1350px; overflow: hidden; }
body {
  font-family: "Inter", "Noto Sans", sans-serif; color: #10203a;
  background: #f4f8ff; display: flex; flex-direction: column;
}
.slide { width: 1080px; height: 1350px; padding: 78px 80px 120px; display: flex; flex-direction: column; position: relative; }
.center { justify-content: center; }
.dark { background: linear-gradient(150deg,#2F6BFF 0%,#1B46C8 55%,#0A2366 100%); color: #fff; }
.light { background: #f4f8ff; }
.eyebrow { font-size: 26px; letter-spacing: 3px; font-weight: 700; text-transform: uppercase; opacity: .72; margin-bottom: 26px; }
h1 { font-size: 88px; line-height: 1.03; letter-spacing: -2.5px; font-weight: 800; }
h2 { font-size: 68px; line-height: 1.06; letter-spacing: -1.8px; font-weight: 800; }
h3 { font-size: 38px; font-weight: 700; }
p.lead { font-size: 38px; line-height: 1.38; margin-top: 28px; opacity: .92; }
.chips { display: flex; gap: 16px; flex-wrap: wrap; margin-top: 40px; }
.chip { border: 2px solid rgba(255,255,255,.55); border-radius: 999px; padding: 14px 26px; font-size: 27px; font-weight: 600; }
.chip.dark { border-color: #c8d8f8; color: #16325f; background: #fff; }
.logo { width: 230px; border-radius: 56px; box-shadow: 0 30px 60px rgba(0,0,0,.35); }
.footer { position: absolute; left: 80px; right: 80px; bottom: 44px; background: linear-gradient(to top, #f4f8ff 55%, rgba(244,248,255,0)); padding-top: 26px; display: flex; justify-content: space-between; font-size: 24px; opacity: .62; }
.badge { display: inline-block; background: #e8f0ff; color: #12306e; border-radius: 16px; padding: 10px 20px; font-size: 26px; font-weight: 700; }
.dark .badge { background: rgba(255,255,255,.18); color: #fff; }
.dark .footer { background: linear-gradient(to top, #0A2366 55%, rgba(10,35,102,0)); }
.row { display: flex; gap: 34px; align-items: center; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; margin-top: 34px; }
.card { background: #fff; border: 2px solid #dbe6fa; border-radius: 26px; padding: 26px 28px; box-shadow: 0 12px 28px rgba(20,45,100,.08); }
.card h4 { font-size: 33px; margin-bottom: 8px; color: #12306e; }
.card p { font-size: 26px; line-height: 1.3; color: #46536b; }
.phone { width: auto; max-height: 560px; max-width: 100%; border-radius: 44px; border: 10px solid #0b1220; box-shadow: 0 30px 60px rgba(10,30,80,.35); }
.big { font-size: 150px; font-weight: 800; letter-spacing: -6px; color: #1b4aa8; line-height: 1; }
.stat { display: flex; gap: 26px; margin-top: 26px; }
.stat div { flex: 1; background: #fff; border: 2px solid #dbe6fa; border-radius: 24px; padding: 22px; text-align: center; }
.stat b { display: block; font-size: 54px; color: #12306e; }
.stat span { font-size: 23px; color: #4d5b73; }
ul.clean { list-style: none; margin-top: 26px; }
ul.clean li { font-size: 33px; line-height: 1.4; padding-left: 46px; position: relative; margin-bottom: 16px; }
ul.clean li:before { content: "✓"; position: absolute; left: 0; color: #2F6BFF; font-weight: 800; }
.dark ul.clean li:before { color: #9fc4ff; }
ul.no li:before { content: "✕"; color: #c0392b; }
.split { display: flex; gap: 26px; margin-top: 30px; }
.split .col { flex: 1; border-radius: 26px; padding: 28px; }
.col.cloud { background: #fbeceb; border: 2px solid #f3c9c5; }
.col.offline { background: #e9f7ef; border: 2px solid #b9e4cb; }
.col h3 { font-size: 32px; margin-bottom: 16px; }
.col li { font-size: 25px; line-height: 1.35; margin: 0 0 10px 18px; }
.small { font-size: 24px; opacity: .7; margin-top: 30px; line-height: 1.35; }
"""

def slide(body: str, cls: str = "light", num: int = 0, total: int = 8, lang: str = "en") -> str:
    label = f"{num}/{total} · VaultZero"
    link = "github.com/cipherwell/Vault-Zero"
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body><section class="slide {cls}">{body}
<div class="footer"><span>{label}</span><span>{link}</span></div></section></body></html>"""

def phone(path: Path, cls: str = "phone") -> str:
    return f'<img class="{cls}" src="file://{path}">'

def build(lang: str, target: Path) -> None:
    A = HERE / "assets"
    es = lang == "es"
    shots = (A / "es") if es else A
    t = {
      "cover_eyebrow": "VaultZero · beta" if not es else "VaultZero · beta",
      "cover_h1": "Your private vault." if not es else "Tu bóveda privada.",
      "cover_lead": "Passwords, cards, 2FA codes and notes — encrypted on your phone." if not es
                    else "Contraseñas, tarjetas, códigos 2FA y notas, cifrados en tu teléfono.",
      "chips": ["No account", "No cloud", "No internet permission"] if not es
               else ["Sin cuenta", "Sin nube", "Sin permiso de internet"],
      "s2_eyebrow": "One vault" if not es else "Una sola bóveda",
      "s2_h": "Everything private, in one place." if not es else "Todo lo privado, en un solo lugar.",
      "types": [("Logins","Passwords, usernames, 2FA"),("Cards","Number, expiry, CVV, PIN"),
                ("API secrets","Keys, tokens, endpoints"),("Wallets","Seed phrases, private keys"),
                ("Notes","Encrypted free text"),("Identities","IDs, passports, documents")] if not es else
               [("Inicios","Contraseñas, usuarios, 2FA"),("Tarjetas","Número, vencimiento, CVV, PIN"),
                ("Secretos API","Claves, tokens, endpoints"),("Billeteras","Frases semilla, claves privadas"),
                ("Notas","Texto libre cifrado"),("Identidades","IDs, pasaportes, documentos")],
      "s3_eyebrow": "Security, simply" if not es else "Seguridad, en simple",
      "s3_h": "Sealed on your phone." if not es else "Sellado en tu teléfono.",
      "s3_items": ["Master Password → Argon2id key (64 MiB, deliberately slow)",
                   "AES-256-GCM with a unique IV per record",
                   "Tampering is detected, not ignored",
                   "The password itself is never stored"] if not es else
                  ["Contraseña maestra → clave Argon2id (64 MiB, lenta a propósito)",
                   "AES-256-GCM con IV único por registro",
                   "La alteración se detecta, no se ignora",
                   "La contraseña nunca se guarda"],
      "s4_eyebrow": "Tools" if not es else "Herramientas",
      "s4_h": "Built to keep you safer." if not es else "Hechas para protegerte mejor.",
      "s4_cards": [("Generator","Strong passwords & passphrases, on-device"),
                   ("2FA codes","6-digit codes that refresh every 30 s"),
                   ("Security check-up","Weak, reused, stale passwords and 2FA errors")] if not es else
                  [("Generador","Contraseñas y frases fuertes, en el dispositivo"),
                   ("Códigos 2FA","6 dígitos que cambian cada 30 s"),
                   ("Auditoría","Débiles, repetidas, antiguas y errores de 2FA")],
      "s5_eyebrow": "Different by design" if not es else "Diferente por diseño",
      "s5_h": "No cloud to breach." if not es else "Sin nube que vulnerar.",
      "s5_cloud": ["Account + email required","INTERNET permission","Telemetry & analytics","Vendor can be breached or subpoenaed"] if not es else
                  ["Requiere cuenta y correo","Permiso de INTERNET","Telemetría y analítica","El proveedor puede ser vulnerado o citado"],
      "s5_off": ["No account, no servers","No INTERNET permission at all","Zero telemetry","Only you hold the key"] if not es else
                ["Sin cuenta ni servidores","Sin permiso de INTERNET","Cero telemetría","Solo tú tienes la llave"],
      "s6_eyebrow": "Honest security" if not es else "Seguridad honesta",
      "s6_h": "What it cannot do." if not es else "Lo que no puede hacer.",
      "s6_items": ["Stop malware or a keylogger on your phone",
                   "Fix a weak or reused Master Password",
                   "Protect a rooted or physically compromised device",
                   "Recover the vault if you lose both the password and the kit — Reset erases it"] if not es else
                  ["Detener malware o un keylogger en tu teléfono",
                   "Arreglar una contraseña maestra débil o repetida",
                   "Proteger un dispositivo rooteado o comprometido",
                   "Recuperar la bóveda si pierdes la contraseña y el kit: Restablecer la borra"],
      "s6_small": "No app can promise perfect security. VaultZero promises smaller attack surface, not magic." if not es else
                  "Ninguna app promete seguridad perfecta. VaultZero promete menos superficie de ataque, no magia.",
      "s7_eyebrow": "Self-audited · public beta" if not es else "Autoauditada · beta pública",
      "s7_h": "Receipts, not slogans." if not es else "Hechos, no eslóganes.",
      "s7_stats": [("0","Critical / High"),("137","automated tests"),("15/15","on-device tests")] if not es else
                  [("0","Críticos / Altos"),("137","pruebas automatizadas"),("15/15","pruebas en dispositivo")],
      "s7_small": "Internal OWASP MASVS 2.1 audit + pentest · 2 Medium bugs fixed · independent audit still pending · beta: test data only" if not es else
                  "Auditoría y pentest internos OWASP MASVS 2.1 · 2 fallos Medios corregidos · falta auditoría independiente · beta: solo datos de prueba",
      "s8_eyebrow": "Beta" if not es else "Beta",
      "s8_h": "Try it. Then read the guide." if not es else "Pruébala. Luego lee la guía.",
      "s8_bullets": ["Android 7.0 or newer","GitHub Releases · Obtainium · F-Droid","English & Spanish","Beta: use test data only"] if not es else
                   ["Android 7.0 o superior","GitHub Releases · Obtainium · F-Droid","Inglés y español","Beta: usa solo datos de prueba"],
      "s8_cta": "Guide + downloads in the first comment." if not es else "Guía y descargas en el primer comentario.",
    }
    def s2():
        cards = "".join(f'<div class="card"><h4>{html.escape(a)}</h4><p>{html.escape(b)}</p></div>' for a,b in t["types"])
        return f'<div class="eyebrow">{t["s2_eyebrow"]}</div><h2>{t["s2_h"]}</h2><div class="grid" style="grid-template-columns:1fr 1fr 1fr">{cards}</div>' \
               f'<div style="margin-top:auto;text-align:center"><img class="phone" style="max-height:430px" src="file://{shots / "03-vault.png"}"></div>'
    def s3():
        items = "".join(f"<li>{html.escape(x)}</li>" for x in t["s3_items"])
        return f'<div class="eyebrow">{t["s3_eyebrow"]}</div><h2>{t["s3_h"]}</h2>' \
               f'<div class="row" style="flex:1;align-items:center"><div style="flex:1">' \
               f'<ul class="clean">{items}</ul><p class="small">Argon2id → key · AES-256-GCM · Keystore biometrics</p></div>' \
               f'<img class="phone" style="max-height:620px" src="file://{shots / "08-lock.png"}"></div>'
    def s4():
        cards = "".join(f'<div class="card"><h4>{a}</h4><p>{b}</p></div>' for a,b in t["s4_cards"])
        return f'<div class="eyebrow">{t["s4_eyebrow"]}</div><h2>{t["s4_h"]}</h2><div class="grid" style="grid-template-columns:1fr 1fr 1fr">{cards}</div>' \
               f'<div style="margin-top:auto;text-align:center">{phone(shots / "05-generator.png", "phone")}</div>'
    def s5():
        c = "".join(f"<li>{html.escape(x)}</li>" for x in t["s5_cloud"])
        o = "".join(f"<li>{html.escape(x)}</li>" for x in t["s5_off"])
        return f'<div class="eyebrow">{t["s5_eyebrow"]}</div><h2>{t["s5_h"]}</h2><div class="split">' \
               f'<div class="col cloud"><h3>Cloud managers</h3><ul>{c}</ul></div>' \
               f'<div class="col offline"><h3>VaultZero</h3><ul>{o}</ul></div></div>' \
               f'<p class="small">Trade-off: backups are manual. That is the point.</p>' if not es else \
               f'<div class="eyebrow">{t["s5_eyebrow"]}</div><h2>{t["s5_h"]}</h2><div class="split">' \
               f'<div class="col cloud"><h3>Gestores en la nube</h3><ul>{c}</ul></div>' \
               f'<div class="col offline"><h3>VaultZero</h3><ul>{o}</ul></div></div>' \
               f'<p class="small">Intercambio: las copias son manuales. Ese es el punto.</p>'
    def s6():
        items = "".join(f"<li>{html.escape(x)}</li>" for x in t["s6_items"])
        beta = "BETA RULE: use test data, not your only copy" if not es else "REGLA BETA: datos de prueba, no tu única copia"
        return f'<div class="eyebrow">{t["s6_eyebrow"]}</div><h2>{t["s6_h"]}</h2><ul class="clean no">{items}</ul>' \
               f'<p class="small">{html.escape(t["s6_small"])}</p>' \
               f'<div style="margin-top:auto"><span class="badge">{beta}</span></div>'
    def s7():
        stats = "".join(f'<div><b>{a}</b><span>{b}</span></div>' for a,b in t["s7_stats"])
        return f'<div class="eyebrow">{t["s7_eyebrow"]}</div><h2>{t["s7_h"]}</h2><div class="stat">{stats}</div>' \
               f'<p class="small">{html.escape(t["s7_small"])}</p>' \
               f'<div style="margin-top:20px;flex:1;display:flex;align-items:center;justify-content:center"><img class="phone" style="max-height:640px" src="file://{shots / "06-audit.png"}"></div>'
    def s8():
        items = "".join(f"<li>{html.escape(x)}</li>" for x in t["s8_bullets"])
        return f'<div style="text-align:center"><img class="logo" style="width:150px;border-radius:38px" src="file://{A / "icon.png"}"></div>' \
               f'<div class="eyebrow" style="text-align:center;margin-top:34px">{t["s8_eyebrow"]}</div><h2 style="text-align:center">{t["s8_h"]}</h2>' \
               f'<ul class="clean" style="max-width:820px;margin:34px auto 0">{items}</ul>' \
               f'<p class="lead" style="text-align:center;margin-top:auto">{html.escape(t["s8_cta"])}</p>'

    cover = f'<div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">' \
            f'<img class="logo" src="file://{A / "icon.png"}"><div class="badge" style="margin-top:38px;font-size:30px;letter-spacing:2px">{("OPEN BETA" if not es else "BETA ABIERTA")}</div>' \
            f'<h1 style="margin-top:26px">{t["cover_h1"]}</h1>' \
            f'<p class="lead" style="max-width:840px">{t["cover_lead"]}</p>' \
            f'<div class="chips" style="justify-content:center">' + "".join(f'<span class="chip">{c}</span>' for c in t["chips"]) + '</div></div>'
    slides = [cover, s2(), s3(), s4(), s5(), s6(), s7(), s8()]
    total = len(slides)
    for i, body in enumerate(slides, 1):
        cls = ("dark" if i in (1, 8) else "light") + (" center" if i in (5, 6) else "")
        doc = slide(body, cls, i, total, lang)
        html_path = target / f"slide-{i:02d}.html"
        html_path.write_text(doc, encoding="utf-8")
        png = target / f"{i:02d}.png"
        subprocess.run(["chromium", "--headless", "--disable-gpu", "--no-sandbox",
                        "--hide-scrollbars", f"--window-size=1080,1350",
                        f"--screenshot={png}", f"file://{html_path}"],
                       check=True, capture_output=True)
        print(f"  {png.name}")
    # square summary
    square = f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}
    html,body{{width:1080px;height:1080px}} .slide{{height:1080px;padding:70px 76px}}
    </style></head><body><section class="slide dark">
    <div style="display:flex;gap:40px;align-items:center">
      <img class="logo" style="width:180px" src="file://{A / 'icon.png'}">
      <div><div class="eyebrow" style="margin:0">VaultZero · beta</div>
      <h2 style="font-size:62px">{t['cover_h1']}</h2></div></div>
    <p class="lead" style="font-size:34px">{t['cover_lead']}</p>
    <div class="chips">{"".join(f'<span class="chip">{c}</span>' for c in t['chips'])}</div>
    <p class="lead" style="font-size:30px;opacity:.85">Open source · Self-audited · Independent audit pending</p>
    <div class="footer" style="background:none"><span>VaultZero</span><span>github.com/cipherwell/Vault-Zero</span></div>
    </section></body></html>""" if not es else f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}
    html,body{{width:1080px;height:1080px}} .slide{{height:1080px;padding:70px 76px}}
    </style></head><body><section class="slide dark">
    <div style="display:flex;gap:40px;align-items:center">
      <img class="logo" style="width:180px" src="file://{A / 'icon.png'}">
      <div><div class="eyebrow" style="margin:0">VaultZero · beta</div>
      <h2 style="font-size:62px">{t['cover_h1']}</h2></div></div>
    <p class="lead" style="font-size:34px">{t['cover_lead']}</p>
    <div class="chips">{"".join(f'<span class="chip">{c}</span>' for c in t['chips'])}</div>
    <p class="lead" style="font-size:30px;opacity:.85">Código abierto · Autoauditada · Auditoría pendiente</p>
    <div class="footer" style="background:none"><span>VaultZero</span><span>github.com/cipherwell/Vault-Zero</span></div>
    </section></body></html>"""
    sq_html = OUT / ("square-es.html" if es else "square-en.html")
    sq_html.write_text(square, encoding="utf-8")
    subprocess.run(["chromium", "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
                    "--window-size=1080,1080", f"--screenshot={OUT / 'square' / ('vaultzero-square-es.png' if es else 'vaultzero-square-en.png')}",
                    f"file://{sq_html}"], check=True, capture_output=True)

if __name__ == "__main__":
    print("EN:"); build("en", EN)
    print("ES:"); build("es", ES)
