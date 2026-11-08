# Skills for this repository

Claude Code sessions on this repository load every skill in this folder.

| Skill | Source | Use |
|---|---|---|
| `lockd-in-design` | this project | Cream & Ink rules for every Lockd In screen. **Wins over the others** on colour, type, tone and layout. |
| `ui-ux-pro-max` | [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) v2.15.0, commit 4f2dbb6, MIT (`LICENSE-ui-ux-pro-max`) | UX rules, accessibility checks, stack guidance (react, html-tailwind, jetpack-compose, winui), searchable local data |
| `design-system`, `ui-styling`, `brand`, `design`, `banner-design`, `slides` | same repository | token architecture, Tailwind/shadcn, brand voice, logos and banners, HTML slides |

Imported unchanged on 2026-11-07 (owner request) so they can be updated by copying a newer
`.claude/skills/` from upstream.

## Notes for sessions
- The upstream instructions call scripts through `${CLAUDE_PLUGIN_ROOT}`, which is only set when installed as a
  plugin. From this repository, use the repository path instead, for example:
  `python3 .claude/skills/ui-ux-pro-max/scripts/search.py "focus timer study app" --design-system`
- Treat search results as suggestions. The approved Lockd In direction (`lockd-in-design`, `docs/design-spec.md`,
  `docs/product-decisions.md`) and the owner's decisions take priority.
- Some optional scripts reach the network: `design/scripts/logo|icon|cip` call image-generation services and need
  your own API key; `design-system/scripts/fetch-background.py` points to Pexels photos;
  `ui-styling/scripts/shadcn_add.py` runs `npx shadcn`. None run unless a session calls them.
