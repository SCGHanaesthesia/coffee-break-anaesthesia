# Editorial guide for the weekly run

This file tells the scheduled weekly run how to write each issue of **Coffee Break · Anaesthesia**.
The reader is a consultant anaesthetist in Perth, Western Australia (ANZCA fellow, private and public practice,
teaches and examines). The issue should be a genuine 10-minute coffee-break read.

## 1. Before you start

1. Read `feed.json`. Take the newest issue's `published` date.
   **If it was published less than 5 days ago, stop: don't write, commit or push anything.**
2. The new issue's window runs from the day after the previous issue's `window.to` up to today (Perth time),
   normally 7 days. The issue `id` is today's Perth date (`YYYY-MM-DD`), and `number` is the previous number + 1.

## 2. Where to look (all need date-checking)

- **Anaesthesia journals:** BJA, Anaesthesia, Anesthesiology, Anesthesia & Analgesia, European Journal of Anaesthesiology,
  Regional Anesthesia & Pain Medicine, Anaesthesia and Intensive Care, Canadian Journal of Anesthesia, Journal of Clinical
  Anesthesia (BJA Open / Anaesthesia Reports only if notable).
- **General and adjacent journals:** anaesthesia-, perioperative-, pain- or critical-care-relevant papers in NEJM, JAMA,
  Lancet, BMJ, JAMA Surgery, JAMA Network Open, Intensive Care Medicine, Critical Care Medicine, Annals of Surgery, BJS.
  Watch for simultaneous publications at big meetings (ESICM LIVES, ANESTHESIOLOGY, Euroanaesthesia, ASA/NZSA CSC, ASRA).
- **Guidelines, safety and supply:** ANZCA news and safety alerts, ASA (Australia), NZSA, TGA medicine shortages
  (anaesthetic agents, NMBs and reversal, local anaesthetics, vasopressors, fluids), TGA and FDA safety communications,
  ACSQHC, Association of Anaesthetists, RCoA, American Society of Anesthesiologists, ESAIC, DAS, OAA.
- Useful routes when publisher pages block automated reading: doi.org, Crossref, Semantic Scholar, OpenAlex, Europe PMC,
  ScienceDirect article pages, and publisher press releases.

Use parallel subagents (for example one each for anaesthesia journals, general journals, and guidelines/safety/news) if
that's available, then merge.

## 3. What makes the cut

- Only items first published online (or genuinely new) inside the window. A big older paper can appear once, clearly
  labelled as a catch-up with its real date, and never in a "this week" framing.
- Prioritise: multicentre RCTs, large cohorts, meta-analyses, guidelines, safety alerts, drug shortages that affect
  anaesthesia, and anything likely to be talked about in theatre.
- De-prioritise: small single-centre pilots, case reports, narrow lab work, correspondence.
- Aim for: 3 TL;DR lines, 5–8 **Research** items, 1–4 **Safety & supply** items, 1–4 **Guidelines & news** items,
  4–8 **Quick hits** (one line each), and **Dates ahead** for the next 6 weeks. If it was a quiet week, say so in the
  title or coverage note rather than padding.

## 4. How to write each item

- All text in your own words, in Australian/British spelling. No sentences copied from abstracts; any direct quote
  under 15 words. Prefer reported numbers (n, effect size, 95% CI) over adjectives.
- `finding`: what was done and found, in 2–3 sentences, with the headline numbers.
- `trust`: one or two sentences on how much weight it bears (design, size, bias, generalisability).
- `soWhat`: what a practising anaesthetist might do or think differently. Never claim more than the evidence supports.
- `verdict`, one of:
  - `act`: action needed (a safety alert, shortage or dose change)
  - `change`: could change practice (strong new evidence)
  - `consider`: worth considering
  - `reassure`: supports current practice
  - `know`: good to know
- `tags`: 1–3 short topic tags, such as Airway, Regional, Obstetric, Paediatric, Cardiac, Periop medicine, Pain, Safety,
  Supply, Pharmacology, Education, Critical care, Catch-up.
- `url`: prefer `https://doi.org/<DOI>`; otherwise the official page you actually opened.

## 5. Verify before publishing

Check every item against its source before committing (ideally with a separate subagent that didn't write it):
the date, every number, the URL, and that the `soWhat` doesn't overreach. Take extra care with drug doses, supply dates
and safety-alert wording. Fix or drop anything you can't verify, and note gaps in `coverage`.

## 6. Publish

1. Write `issues/<id>.json` following the schema below, which is exactly the shape of `issues/2026-09-27.json`.
2. Run `python3 tools/build_feed.py`. It validates every issue and rebuilds `feed.json`; fix anything it reports.
3. Commit only `issues/<id>.json` and `feed.json`, with the message `Issue <number>: <title>`, and push to `main`.
   The Android app picks the new issue up within about an hour and notifies the reader.
4. Don't modify the app, tools or earlier issues unless correcting a factual error; if you do correct one, keep its
   `id` and `number` and say what changed in the commit message.

## Schema

```json
{
  "id": "YYYY-MM-DD", "number": 2, "title": "Short headline for the week",
  "published": "YYYY-MM-DDTHH:MM:SS+08:00",
  "window": { "from": "YYYY-MM-DD", "to": "YYYY-MM-DD" },
  "readMinutes": 10,
  "tldr": ["line 1", "line 2", "line 3"],
  "sections": [
    { "id": "research", "title": "Research", "items": [
      { "id": "kebab-case-id", "title": "...", "source": "BJA", "date": "YYYY-MM-DD", "dateNote": "optional",
        "tags": ["Airway"], "design": "RCT · n=…", "finding": "...", "trust": "...", "soWhat": "...",
        "verdict": "consider", "url": "https://doi.org/..." } ] },
    { "id": "safety", "title": "Safety & supply", "items": [] },
    { "id": "news", "title": "Guidelines & news", "items": [] }
  ],
  "quickHits": [ { "text": "...", "source": "...", "date": "YYYY-MM-DD", "url": "https://..." } ],
  "dates": [ { "date": "YYYY-MM-DD", "label": "..." } ],
  "coverage": "What couldn't be checked this week, if anything."
}
```
