# Frontend design consistency

Before creating or changing a frontend screen, read `docs/ui-design.md`.
Use `web/css/design-tokens.css` for shared colors and future design tokens, and
`web/js/theme.js` for theme selection. Do not create a separate theme per screen.

The current homepage is an exploratory prototype, not an approved layout template.
Preserve the existing header labels and destinations unless the user requests a change.
Keep frontend work separate from Java/DAO/database and tournament algorithm changes.
User instructions take precedence over these defaults.
