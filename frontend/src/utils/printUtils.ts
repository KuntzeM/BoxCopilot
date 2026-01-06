/**
 * Print Handler Utilities - Cross-Platform Support
 * 
 * Provides utilities for handling print operations across desktop and mobile browsers.
 * Addresses known issues with print-color-adjust on Android and older WebKit browsers.
 */

/**
 * Detects the user's current browser and platform
 * @returns {Object} Browser info: { name, version, isAndroid, isIOS, isMobile }
 */
export function detectBrowser() {
  const ua = navigator.userAgent;
  
  const isAndroid = /Android/i.test(ua);
  const isIOS = /iPhone|iPad|iPod/i.test(ua);
  const isMobile = isAndroid || isIOS;
  
  let name = 'Unknown';
  let version = 'Unknown';
  
  if (/Chrome/.test(ua) && !/Chromium/.test(ua)) {
    name = 'Chrome';
    version = ua.match(/Chrome\/(\d+)/)?.[1] || 'Unknown';
  } else if (/Firefox/.test(ua)) {
    name = 'Firefox';
    version = ua.match(/Firefox\/(\d+)/)?.[1] || 'Unknown';
  } else if (/Safari/.test(ua) && !/Chrome/.test(ua)) {
    name = 'Safari';
    version = ua.match(/Version\/(\d+)/)?.[1] || 'Unknown';
  } else if (/Samsung/.test(ua)) {
    name = 'Samsung Internet';
    version = ua.match(/SamsungBrowser\/(\d+)/)?.[1] || 'Unknown';
  }
  
  return {
    name,
    version: parseInt(version),
    isAndroid,
    isIOS,
    isMobile,
    ua,
  };
}

/**
 * Checks if the browser has known print-color-adjust issues
 * @returns {boolean} True if browser has known issues
 */
export function hasPrintColorAdjustIssue() {
  const browser = detectBrowser();
  
  // Android Chrome < 136 has inconsistent support
  if (browser.isAndroid && browser.name === 'Chrome' && browser.version < 136) {
    return true;
  }
  
  // Samsung Internet < 29 has partial support
  if (browser.name === 'Samsung Internet' && browser.version < 29) {
    return true;
  }
  
  return false;
}

/**
 * Enhanced print handler with platform-specific workarounds
 * @param {Function} onBeforePrint - Callback before print dialog opens
 * @param {number} timeout - Milliseconds to wait before opening print dialog (default: 100)
 */
export function safePrint(onBeforePrint, timeout = 100) {
  const browser = detectBrowser();
  
  // Android needs more time for canvas rendering
  const finalTimeout = browser.isAndroid ? Math.max(timeout, 150) : timeout;
  
  if (onBeforePrint) {
    onBeforePrint();
  }
  
  // Force layout recalculation on Android before print
  if (browser.isAndroid) {
    // Trigger reflow to ensure styles are applied
    document.body.offsetHeight; // Force reflow
  }
  
  setTimeout(() => {
    try {
      window.print();
    } catch (error) {
      console.error('[Print] Error opening print dialog:', error);
      // Fallback: at least show the print dialog even if there was an error
      setTimeout(() => window.print(), 100);
    }
  }, finalTimeout);
}

/**
 * Validates print styles on page load
 * Warns if print-color-adjust support is missing
 */
export function validatePrintStyles() {
  const browser = detectBrowser();
  
  if (process.env.NODE_ENV !== 'production') {
    console.info(
      `[Print] Detected: ${browser.name} ${browser.version} on ${browser.isMobile ? 'Mobile' : 'Desktop'} (${browser.isAndroid ? 'Android' : browser.isIOS ? 'iOS' : 'Other'})`
    );
  }
  
  if (hasPrintColorAdjustIssue()) {
    console.warn(
      `[Print] ⚠️ This browser (${browser.name} ${browser.version}) has known print-color-adjust limitations. ` +
      `Consider upgrading for best print results.`
    );
  }
  
  // Check if CSS rules are properly applied
  const printArea = document.querySelector('.print-area');
  if (printArea) {
    const computedStyle = window.getComputedStyle(printArea);
    if (process.env.NODE_ENV !== 'production') {
      console.info('[Print] Print area computed styles:', {
        visibility: computedStyle.visibility,
        display: computedStyle.display,
        // Note: print-color-adjust won't show in computed styles until print context
      });
    }
  }
}

/**
 * Mobile-specific print initialization
 * Disables zoom during print to ensure proper rendering
 */
export function initializeMobilePrint() {
  const browser = detectBrowser();
  
  if (browser.isMobile) {
    // Add viewport meta tag adjustment for print
    const viewport = document.querySelector('meta[name="viewport"]');
    if (viewport) {
      const originalContent = viewport.getAttribute('content');
      
      // Before print: restrict zoom
      window.addEventListener('beforeprint', () => {
        viewport.setAttribute('content', originalContent + ', user-scalable=no');
      });
      
      // After print: restore
      window.addEventListener('afterprint', () => {
        viewport.setAttribute('content', originalContent);
      });
    }
  }
}

/**
 * Inject inline styles for problematic browsers
 * Used as fallback if GlobalStyles doesn't apply properly
 */
export function injectPrintStyles() {
  const browser = detectBrowser();
  const style = document.createElement('style');
  
  // Base print styles everyone needs
  let css = `
    @media print {
      html, body {
        margin: 0;
        padding: 0;
        width: 100%;
        height: 100%;
      }
      
      * {
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
        color-adjust: exact !important;
      }
      
      .print-area, .print-area * {
        visibility: visible !important;
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
      
      canvas {
        display: block;
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
    }
  `;
  
  // Additional rules for Android
  if (browser.isAndroid) {
    css += `
      @media print {
        .print-area {
          position: relative;
          width: 100%;
        }
        
        body * {
          visibility: hidden !important;
        }
        
        .print-area,
        .print-area * {
          visibility: visible !important;
        }
      }
    `;
  }
  
  style.textContent = css;
  document.head.appendChild(style);
  
  if (process.env.NODE_ENV !== 'production') {
    console.info('[Print] Injected fallback print styles');
  }
}

export default {
  detectBrowser,
  hasPrintColorAdjustIssue,
  safePrint,
  validatePrintStyles,
  initializeMobilePrint,
  injectPrintStyles,
};
