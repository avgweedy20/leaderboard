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
        text: [

          '\u201CI am not led, I lead.\u201D \u2014 Alexander the Great',
          '\u201CWhile I breathe, there is hope.\u201D \u2014 Marcus Aurelius',
          '\u201CI\u2019ll do whatever it takes.\u201D \u2014 Cesare Borgia',
          '\u201CFortune favors the brave.\u201D \u2014 Pliny the Elder',
          'Made by STEM Club President',
          '\u201CIf I cannot bend the will of Heaven, I shall raise Hell.\u201D \u2014 Hannibal',
          '\u201CI came, I saw, I conquered.\u201D \u2014 Julius Caesar',
          '\u201CLet them hate, so long as they fear me.\u201D \u2014 Caligula',
          '\u201CFrom suffering comes glory.\u201D \u2014 Leonidas',
          'Made by Samir Ghimire',
          '\u201CIn this sign, you shall conquer.\u201D \u2014 Constantine',
        ],
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