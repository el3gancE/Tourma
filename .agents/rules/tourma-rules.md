# TOURMA PROJECT AI WORKSPACE RULES & GUIDELINES

## 1. UI & DESIGN SYSTEM RULES
- **Font**: Always use Google Font `Lexend` (`font-family: 'Lexend', sans-serif`).
- **Colors**: Dark Obsidian `#0b0d12`, Charcoal `#12161f`, Surface `#181d29`, Mint Green `#2dd4bf`, Gold Yellow `#fbbf24`.
- **Text Rule (MANDATORY)**: Never include explanation text in parentheses `(...)` on any label, title, button, or view.
- **Scale**: Keep all UI components compact (equivalent to 67% browser zoom scale).
- **Navbar Layout**: Always center-align header menu links using CSS Grid (`grid-template-columns: 1fr auto 1fr`).
- **Views Location**: Place all view JSP files directly inside `web/common/`. Never create duplicate wrapper JSP files in root `web/`.

## 2. CORE ALGORITHMS & BUSINESS RULES
- **Dynamic Team Input (BR-01)**: Do not force static team count upfront; calculate N dynamically from lines pasted in Textarea Live Parser.
- **Original Seeding (BR-03)**: Seed number stays with the team across all stages.
- **BYE Placement (BR-04)**: Assign BYE slots to top seeds if N != 2^k.
- **Custom W-D-L Points (BR-05)**: Configurable win_points (3), draw_points (1), loss_points (0) for Round Robin & Group Stage.
- **Swiss System (BR-06)**: 3 wins advance, 3 losses eliminated. Sequential round lock.
- **Sliding Window Deduction (BR-09)**: When tournament k > W (W=10), add points of tournament k and automatically deduct points of tournament (k - W).
- **Fixed Tiers (BR-10)**: Rolling Series Tiers are strictly fixed to S, A, B, C, D.

## 3. ARCHITECTURE RULES
- **Shared Component**: Use `web/common/component/match-card-node.jsp` & `template.css` as the single 100% reusable match card for all formats and view modes (Bracket View & Normal List View).
- **Decoupled Logic**: Separate algorithm services (SingleEliminationService, DoubleEliminationService, RoundRobinService, SwissSystemService, GroupStageService, RollingWindowService, FifaEloService) from controllers and DAOs.
- **Tech Stack**: Jakarta EE 10 (`jakarta.servlet.*`), Tomcat 10+, MS SQL Server (T-SQL).
