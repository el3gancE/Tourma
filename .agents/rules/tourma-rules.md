# TOURMA PROJECT AI WORKSPACE RULES & GUIDELINES

## 1. UI & DESIGN TOKEN SYSTEM RULES
- **Design Reference**: Always read `docs/ui-design.md` before creating or changing any frontend screen.
- **Single Source of Truth for Tokens**: Always use `web/css/design-tokens.css` for all colors, surfaces, borders, tiers, and celebration tokens, paired with `web/js/theme.js` for theme selection (`data-theme="light|dark"`, `data-accent="wine|blue|green|orange"`, `data-special-colors="on|off"`). Never hardcode per-screen color palettes or create separate themes per screen.
- **Font**: Always use `var(--ui-font)` (`'Readex Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`). Use weight `500` for body, `600` for team names/menus, and `700` for headings/winners.
- **Core Surface & Text Tokens**:
  - Backgrounds & Surfaces: `var(--ui-bg)`, `var(--ui-surface)`, `var(--ui-surface-alt)`, `var(--ui-canvas)`, `var(--ui-match-surface)`, `var(--ui-match-row)`, `var(--ui-match-score)`.
  - Text & Borders: `var(--ui-text)`, `var(--ui-muted)`, `var(--ui-line)`, `var(--ui-line-strong)`, `var(--ui-match-border)`, `var(--ui-connector)`.
  - Accents & Branches: `var(--ui-accent)`, `var(--ui-accent-hover)`, `var(--ui-accent-soft)`, `var(--ui-on-accent)`, `var(--ui-bracket-lower-accent)`, `var(--ui-bracket-lower-soft)`, `var(--ui-bracket-final-accent)`.
  - Tiers & Semantics: `var(--ui-tier-s)`, `var(--ui-tier-a)`, `var(--ui-tier-b)`, `var(--ui-tier-c)`, `var(--ui-tier-d)`, `var(--ui-success)` (`#16a34a`), `var(--ui-danger)` (`#be123c`).
- **Flat & Clean Aesthetic**: Keep surfaces flat and crisp (`box-shadow: none` on banners/text where specified), using `1px` (`--ui-line`) borders and `--ui-surface` / `--ui-surface-alt` layering.
- **Text Rule (MANDATORY)**: Keep copy concise; never include redundant explanation text in parentheses `(...)` on labels, titles, buttons, or views. Never prefix rank numbers, seeds, or sub-tournament indices with `#` (e.g., write `1`, `3-4`, `Seed 1`, not `#1`, `#3-4`, `Seed #1`).
- **Scale & Navbar Layout**: Keep UI components compact; center-align desktop header menu links (`64px` height, `grid-template-columns: 1fr auto 1fr`) with appearance switches on the right.
- **Views Location**: Place all view JSP files directly inside `web/common/`. Never create duplicate wrapper JSP files in root `web/`.

## 2. CORE ALGORITHMS & BUSINESS RULES
- **Dynamic Team Input (BR-01)**: Do not force static team count upfront; calculate N dynamically from lines pasted in Textarea Live Parser.
- **Original Seeding (BR-03)**: Seed number stays with the team across all stages.
- **BYE Placement (BR-04)**: Assign BYE slots to top seeds if N != 2^k.
- **Custom W-D-L Points (BR-05)**: Configurable win_points (3), draw_points (1), loss_points (0) for Round Robin & Group Stage.
- **Swiss System (BR-06)**: 3 wins advance, 3 losses eliminated. Sequential round lock.
- **Sliding Window Deduction (BR-09)**: When tournament k > W, add points of tournament k and automatically deduct expired tournament points outside the active window.
- **Fixed Tiers (BR-10)**: Rolling Series Tiers are strictly fixed to S, A, B, C, D.

## 3. ARCHITECTURE RULES
- **Shared Stylesheets & Components**: Reuse `web/css/ui-base.css`, `web/css/header.css`, `web/css/workspace.css`, `web/css/tournament-workspace.css`, `web/css/tournament-screen.css`, `web/css/bracket-card.css`, `web/css/popup.css`, and JSP components in `web/common/component/` (`header.jsp`, `sidebar.jsp`, `bracket-card.jsp`, `match-card.jsp`, `h2h.jsp`, `new-number-one.jsp`).
- **Decoupled Logic**: Keep frontend changes separate from Java/DAO/database and tournament algorithm services unless a feature explicitly requires backend data support.
- **Tech Stack**: Jakarta EE 10 (`jakarta.servlet.*`), Tomcat 10+, MS SQL Server (T-SQL).
