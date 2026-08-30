import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import TextType from './TextType';

import textTypeCss from './TextType.css';
import widgetCss from './widget.css';

function injectStyles(css) {
  const style = document.createElement('style');
  style.setAttribute('data-widget-css', 'true');
  style.textContent = css;
  document.head.appendChild(style);
}

injectStyles(textTypeCss + '\n' + widgetCss);

function mountWidgets() {
  const footerRoot = document.querySelector('[data-widget="texttype"]');
  if (!footerRoot) return;

  try {
    createRoot(footerRoot).render(
      createElement(TextType, {
        as: 'span',
        className: 'footer-type',
        text: ['Made by Samir Ghimire', 'Made by STEM Club President'],
        typingSpeed: 75,
        deletingSpeed: 50,
        pauseDuration: 1500,
        showCursor: true,
        cursorCharacter: '_',
        cursorBlinkDuration: 0.5
      })
    );
    window.DSSWidgets = { ready: true };
  } catch (e) {
    console.error('[widgets] footer TextType mount failed:', e);
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mountWidgets);
} else {
  mountWidgets();
}