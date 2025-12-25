// i18n index - exports and helper functions

import { de } from './locales/de';
import { en } from './locales/en';
import { Language } from './types';

export const translations = {
  de,
  en,
};

// Simple translation function with support for variable interpolation
export function getTranslation(language: Language, key: string): string {
  const keys = key.split('.');
  let value: any = translations[language];

  for (const k of keys) {
    if (value && typeof value === 'object') {
      value = value[k];
    } else {
      console.warn(`Translation key not found: ${key} for language ${language}`);
      return key;
    }
  }

  return typeof value === 'string' ? value : key;
}

// Helper function to replace variables in translation strings
// Example: t('boxes.boxNumber', { number: 5 }) => "Box #5"
export function interpolate(text: string, vars?: Record<string, string | number>): string {
  if (!vars) return text;
  
  let result = text;
  Object.entries(vars).forEach(([key, value]) => {
    result = result.replace(`{{${key}}}`, String(value));
  });
  
  return result;
}

export type { Language, LanguageContextType } from './types';
