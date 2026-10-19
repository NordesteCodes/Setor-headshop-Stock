---
name: Obsidian Botanica
colors:
  surface: '#101418'
  surface-dim: '#101418'
  surface-bright: '#36393f'
  surface-container-lowest: '#0b0e13'
  surface-container-low: '#191c21'
  surface-container: '#1d2025'
  surface-container-high: '#272a2f'
  surface-container-highest: '#32353a'
  on-surface: '#e1e2e9'
  on-surface-variant: '#bbcabf'
  inverse-surface: '#e1e2e9'
  inverse-on-surface: '#2e3036'
  outline: '#86948a'
  outline-variant: '#3c4a42'
  surface-tint: '#4edea3'
  primary: '#4edea3'
  on-primary: '#003824'
  primary-container: '#10b981'
  on-primary-container: '#00422b'
  inverse-primary: '#006c49'
  secondary: '#ffb95f'
  on-secondary: '#472a00'
  secondary-container: '#ee9800'
  on-secondary-container: '#5b3800'
  tertiary: '#ffb3ad'
  on-tertiary: '#68000a'
  tertiary-container: '#ff7a73'
  on-tertiary-container: '#79000e'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#6ffbbe'
  primary-fixed-dim: '#4edea3'
  on-primary-fixed: '#002113'
  on-primary-fixed-variant: '#005236'
  secondary-fixed: '#ffddb8'
  secondary-fixed-dim: '#ffb95f'
  on-secondary-fixed: '#2a1700'
  on-secondary-fixed-variant: '#653e00'
  tertiary-fixed: '#ffdad7'
  tertiary-fixed-dim: '#ffb3ad'
  on-tertiary-fixed: '#410004'
  on-tertiary-fixed-variant: '#930013'
  background: '#101418'
  on-background: '#e1e2e9'
  surface-variant: '#32353a'
typography:
  headline-xl:
    fontFamily: Geist
    fontSize: 36px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-xl-mobile:
    fontFamily: Geist
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Geist
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Geist
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Geist
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  body-lg:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-numeric:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-code:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.05em
  label-tag:
    fontFamily: Geist
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

The design system is crafted for high-volume smoke shops, dispensaries, and boutique headshops that demand enterprise-grade rigor without sacrificing subcultural authenticity. The interface shifts away from stereotypical psychedelic tropes, adopting an understated, luxury botanical and obsidian aesthetic. It evokes precision, craftsmanship, and control—giving retail managers and multi-store operators the clarity of a modern financial terminal.

### Design Movement & Aesthetic
- **Tactile Technical Minimalism:** Dense information display paired with subtle physical depth, clean geometric contours, and soft inner glows reminiscent of machined anodized metal and matte smoked glass.
- **Tone & Mood:** Discreet, hyper-efficient, disciplined, and refined. High contrast for instant SKU recognition under retail point-of-sale lighting, with dark mode as the primary canvas to reduce ocular strain during extended stock takes.

## Colors

The palette is engineered around dark obsidian tones layered with rich botanical emeralds, amber alerts, and precise status indicators. Dark mode is the primary operational mode, with a curated warm off-white mode for well-lit counter environments.

### Core Swatches
- **Primary (`#10B981`):** Herbal Emerald. Represents active inventory, optimal stock levels, verified batches, and positive cash flow metrics.
- **Secondary (`#F59E0B`):** Amber Gold. Reserved for low-stock thresholds, reorder triggers, pending supplier shipments, and batch expiration notices.
- **Tertiary (`#EF4444`):** Deep Crimson. High-severity status: out-of-stock, damaged stock, shrinkage alerts, and barcode mismatch errors.
- **Neutral (`#1C1F24`):** Smoked Graphite. The central container layer anchoring all operational data.

### Dark Mode Surface Structure
- **Canvas Base:** `#141619`
- **Surface Layer 1 (Cards, Sidebars):** `#1C1F24`
- **Surface Layer 2 (Elevated Tables, Modals):** `#242930`
- **Border Subtle (Dividers, Inactive Frames):** `#2E353E`
- **Border Interactive (Inputs, Hover Boundaries):** `#343B44`
- **Foreground Primary:** `#F3F4F6`
- **Foreground Muted:** `#9CA3AF`

### Light Mode Surface Structure (Alternative)
- **Canvas Base:** `#F4F5F7`
- **Surface Layer 1:** `#FFFFFF`
- **Surface Layer 2:** `#E9EBEF`
- **Border Subtle:** `#D1D5DB`
- **Border Interactive:** `#9CA3AF`
- **Foreground Primary:** `#111827`
- **Foreground Muted:** `#4B5563`

## Typography

The type hierarchy balances rapid legibility with precision engineering. **Geist** delivers crisp, modern grotesk readability across large operational screens, while **JetBrains Mono** guarantees tabular alignment for currency values (`R$ 0,00`), EAN-13 barcodes, SKU codes, and stock delta calculations.

### Application Rules
- **Numerical Data & SKU Codes:** Always render quantities, financial figures, tax rates, and SKU tags in `label-numeric` or `label-code` using tabular numbers (`tnum` font feature) to maintain vertical alignment down table columns.
- **Category Labels:** Formatted in uppercase with relaxed tracking (`0.04em`) to establish immediate micro-hierarchy.
- **Headlines:** Keep letter spacing tight (`-0.02em`) on desktop displays to retain a solid, executive tone.

## Layout & Spacing

This design system uses a dense, responsive fluid grid designed for rapid data consumption and POS tablet navigation.

### Breakpoints & Layout Model
- **Desktop (1280px+):** 12-column layout with fixed left collapsible navigation rail (64px collapsed, 240px expanded). High-density layout prioritizing data tables with fixed headers, inline filters, and right-hand drawer inspect panels.
- **Tablet (768px - 1279px):** 8-column layout. Metric cards collapse to a 2x2 grid. Inventory tables enable horizontal scroll with sticky primary identifier columns (SKU + Product Name).
- **Mobile (<768px):** 4-column layout. Data shifts from multi-column tables into modular inventory status cards featuring direct quick-action buttons (Scan, Restock, Adjust).

### Density Philosophy
Maintain tight internal component padding (`space-xs` and `space-sm`) inside table cells and dropdowns to maximize visible items per screen. Let section margins (`margin-desktop`) breathe to avoid cognitive overload.

## Elevation & Depth

Visual hierarchy uses tonal surface layering combined with razor-thin structural borders (`1px`) and soft, low-intensity ambient shadows. Avoid heavy blurred dropshadows that muddy dark backgrounds.

### Elevation Levels
- **Level 0 (Canvas Base - `#141619`):** Application background, base canvas for grid layout.
- **Level 1 (Flat Containers - `#1C1F24`):** Side navigation, data table canvas, persistent metric panels. Border: `1px solid #2E353E`.
- **Level 2 (Interactive Floating Modules - `#242930`):** Modal windows, flyout filter menus, dropdown autocompletes, right-side drawer panels. Shadow: `0 8px 24px -4px rgba(0, 0, 0, 0.45)`. Border: `1px solid #343B44`.
- **Level 3 (Focused & Overlaid Elements):** Tooltips, scanner focus overlays, toast notifications. Shadow: `0 12px 32px -4px rgba(0, 0, 0, 0.65)`. Border: `1px solid #10B981` (primary highlight) or `#343B44`.

## Shapes

The system implements a structured **Soft (`1`)** shape language. Tight corner radiuses (`4px` base, `8px` container) convey high mechanical precision, software reliability, and maximum usable screen area for high-density tables.

### Curvature Usage
- **Inputs, Buttons, Badges, Table Rows:** `rounded-sm` (4px / 0.25rem). Creates crisp alignment along table gridlines.
- **Cards, Panels, Modals:** `rounded-lg` (8px / 0.5rem). Softens container edges while maintaining an architectural feel.
- **Category & Status Pills:** Full pill (`9999px`) exclusively for operational category tags (e.g., *Sedas & Piteiras*) and stock level chips to distinguish categorical data from interactive buttons.

## Components

### Buttons
- **Primary:** Background `#10B981`, foreground `#042F2E`, font weight `500`. On hover, scale brightness up to `#34D399`. Active state slightly recessed.
- **Secondary (Subtle Dark):** Background `#242930`, border `1px solid #343B44`, text `#F3F4F6`. Hover triggers border change to `#4B5563`.
- **Destructive:** Background `rgba(239, 68, 68, 0.1)`, border `1px solid rgba(239, 68, 68, 0.3)`, text `#EF4444`.

### Category & Metric Pills
- **Category Pills (e.g., *Sedas & Piteiras, Bongs & Vidrarias, Vaporizadores, Isqueiros & Maçaricos, Cuia & Tesouras, Tabacos & Ervas*):** Height `22px`, font size `11px`, border-radius `9999px`, padding `0 8px`. Semi-transparent fill (`rgba(255, 255, 255, 0.04)`), crisp border `1px solid #2E353E`, text `#9CA3AF`.
- **Stock Status Badges:**
  - *In Stock:* Tinted background `rgba(16, 185, 129, 0.1)`, text `#10B981`, border `1px solid rgba(16, 185, 129, 0.25)`.
  - *Low Stock Alert:* Tinted background `rgba(245, 158, 11, 0.1)`, text `#F59E0B`, border `1px solid rgba(245, 158, 11, 0.25)`.
  - *Critical / Out of Stock:* Tinted background `rgba(239, 68, 68, 0.1)`, text `#EF4444`, border `1px solid rgba(239, 68, 68, 0.25)`.

### Inventory Data Table
- **Header:** Sticky positioning, background `#141619`, font `11px` uppercase with tracking `0.05em`, color `#6B7280`, bottom border `1px solid #2E353E`.
- **Rows:** Alternating subtle zebra striping (`#1C1F24` and `#181B1F`). Row height `40px` compact, `52px` relaxed. Hover state shifts row to `#242930` with a `2px` left edge accent in `#10B981`.
- **Cells:** Barcodes and SKUs rendered in `JetBrains Mono` with muted tone `#9CA3AF`. Currency values aligned right, formatted in bold monospaced text.

### Inputs & Barcode Search Fields
- **Search Bar / Barcode Field:** Background `#141619`, border `1px solid #343B44`, text `#F3F4F6`. Includes a dedicated monospace indicator icon `[BARCODE]` and keyboard shortcut badge (`⌘K`).
- **Focus State:** Border transitions to `#10B981` with an inner ring glow: `0 0 0 1px #10B981`.

### Checkboxes & Bulk Action Controls
- **Checkboxes:** Size `16x16px`, background `#141619`, border `1px solid #343B44`, border-radius `3px`. Checked state: background `#10B981` with dark glyph `#042F2E`.

### Cards & Metrics
- **Metric Cards (KPIs):** Background `#1C1F24`, border `1px solid #2E353E`, padding `16px`. Displays metric label (e.g., *Valor do Estoque*, *Ruptura de Estoque*), followed by oversized monospaced figure and mini delta sparkline badge (+4.2% vs. past month).