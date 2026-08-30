# DSS React Widget Islands

A small React island that progressively upgrades the vanilla Flask frontend
with one [reactbits](https://reactbits.dev) component:

- **TextType** (footer typewriter) - vendored from `react-bits/src/content/TextAnimations/TextType/`

The component is bundled with esbuild into a single self-contained file:

```
app/static/js/widgets.bundle.js
```

`base.html` loads the bundle before `app.js`. When the bundle exists, the widget
mounts and `window.DSSWidgets.ready` is set; when it is missing (or fails to
load), `app.js` falls back to the vanilla typewriter. No lock-in, no build step
required for the rest of the app.

## Build

Requires Node.js:

```bash
cd react-widgets
npm install
npm run build
```

`npm run watch` rebuilds on every change.

The bundle is also produced automatically in two situations:

- **Local dev** - `python app/app.py` builds the widget on startup when the
  bundle is missing and Node is available (set `SKIP_WIDGET_BUILD=1` to skip).
- **Vercel deploy** - `pyproject.toml` sets the build script
  (`cd react-widgets && npm install && npm run build`), so Node compiles the
  bundle during the deploy build before the Flask function is packaged.

## Structure

```
react-widgets/
  package.json          deps: react, react-dom, gsap; devDep: esbuild
  src/
    widgets.jsx         entry: styles + mounts into [data-widget="texttype"],
                        sets window.DSSWidgets
    TextType.jsx        vendored reactbits TextType (gsap)
    TextType.css        vendored reactbits TextType styles
    widget.css          DSS theme overrides (light + dark via CSS variables)
```

## Notes

- `--loader:.css=text` inlines the component CSS into the bundle and it is
  injected as a `<style data-widget-css>` so there are no extra requests.
- Component source is MIT-licensed (c) the react-bits authors.