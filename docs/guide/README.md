# VaultZero — documentation & guides

Bilingual (English / Spanish) documentation for the VaultZero Android beta.

| Document | Format |
| --- | --- |
| [VaultZero — your private vault (English guide)](VaultZero-Guide-EN.md) | Markdown |
| [VaultZero — tu bóveda privada (guía en español)](VaultZero-Guia-ES.md) | Markdown |
| [English PDF](VaultZero-Guide-EN.pdf) | A4, 19 pages |
| [Spanish PDF](VaultZero-Guia-ES.pdf) | A4, 20 pages |
| [Social share kit](SOCIAL.md) | Short posts in both languages |

## What the guide covers

- What VaultZero is and the 60-second setup.
- Every feature, in plain language: all six record types, search/filters/favourites, the password
  generator, 2FA codes, the security audit, every Settings row, the recovery kit, biometrics,
  backups and moving phones.
- How the security works: Argon2id, AES-256-GCM, record binding, Keystore biometrics, auto-lock,
  screenshot/clipboard protection, and the no-network design.
- An honest comparison with cloud password managers, including trade-offs.
- What it protects against **and what it cannot**.
- Install/update/verify instructions and a summary of the pre-beta security audit.

## Rebuild the PDFs

```bash
cd docs/guide
python3 build_pdfs.py     # requires chromium + python-markdown, A4 via headless print
```

Screenshots in `assets/` were captured from a QA build with screenshots explicitly enabled; the
shipped app blocks screenshots. All sample data (Gmail, GitHub, `example.com`) is fictional.

## License

The guides are part of the VaultZero repository and share its MIT license.

## Social media images

Facebook-ready carousels (1080 × 1350, 8 slides) and square 1080 × 1080 summaries, in English and
Spanish, with copy-ready captions and ZIPs:

- [`social/facebook-en/`](social/facebook-en) · [`social/facebook-es/`](social/facebook-es)
- [`social/square/`](social/square)
- [`social/FACEBOOK-POST.md`](social/FACEBOOK-POST.md)
- [`social/vaultzero-facebook-en.zip`](social/vaultzero-facebook-en.zip) · [`social/vaultzero-facebook-es.zip`](social/vaultzero-facebook-es.zip)
