import React, { createContext, useState, useMemo, ReactNode, useEffect, useCallback } from 'react';
import Cookies from 'js-cookie';
import { Language, LanguageContextType } from '../i18n/types';
import { getTranslation, interpolate } from '../i18n';

export const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const useLanguageContext = () => {
  const context = React.useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguageContext must be used within LanguageProvider');
  }
  return context;
};

interface LanguageProviderProps {
  children: ReactNode;
}

export const LanguageProvider: React.FC<LanguageProviderProps> = ({ children }) => {
  const [language, setLanguageState] = useState<Language>(() => {
    const savedLanguage = Cookies.get('app-language') as Language | undefined;
    return savedLanguage && (savedLanguage === 'de' || savedLanguage === 'en') ? savedLanguage : 'de';
  });

  useEffect(() => {
    Cookies.set('app-language', language, { expires: 365 });
  }, [language]);

  const setLanguage = useCallback((lang: Language) => {
    setLanguageState(lang);
  }, []);

  // Translation function with optional variable interpolation
  const t = useCallback(
    (key: string, vars?: Record<string, string | number>): string => {
      const translation = getTranslation(language, key);
      return vars ? interpolate(translation, vars) : translation;
    },
    [language]
  );

  const value = useMemo(
    () => ({
      language,
      setLanguage,
      t,
    }),
    [language, setLanguage, t]
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
};
