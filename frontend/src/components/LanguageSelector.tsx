import React, { useMemo, useState } from 'react';
import { Button, IconButton, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { useTranslation } from '../hooks/useTranslation';

type Variant = 'icon' | 'button';

interface LanguageSelectorProps {
  variant?: Variant;
  label?: string;
  buttonSx?: SxProps<Theme>;
}

const LanguageSelector: React.FC<LanguageSelectorProps> = ({ variant = 'icon', label, buttonSx }) => {
  const { language, setLanguage, t } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const buttonId = variant === 'icon' ? 'language-icon-button' : 'language-button';
  const flagEmoji = useMemo(() => (language === 'de' ? '🇩🇪' : '🇬🇧'), [language]);
  const labelText = useMemo(() => {
    if (label) return label;
    return language === 'de' ? t('language.german') : t('language.english');
  }, [label, language, t]);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLanguageChange = (lang: 'de' | 'en') => {
    setLanguage(lang);
    handleClose();
  };

  return (
    <>
      {variant === 'icon' ? (
        <IconButton
          id={buttonId}
          onClick={handleClick}
          color="inherit"
          aria-label={t('language.select')}
          aria-controls={open ? 'language-menu' : undefined}
          aria-haspopup="true"
          aria-expanded={open ? 'true' : undefined}
          size="medium"
          sx={[{ ml: 1 }, buttonSx]}
        >
          <span style={{ fontSize: '1.25rem' }}>{flagEmoji}</span>
        </IconButton>
      ) : (
        <Button
          id={buttonId}
          onClick={handleClick}
          color="inherit"
          startIcon={<span style={{ fontSize: '1.25rem' }}>{flagEmoji}</span>}
          aria-label={t('language.select')}
          aria-controls={open ? 'language-menu' : undefined}
          aria-haspopup="true"
          aria-expanded={open ? 'true' : undefined}
          sx={[{ justifyContent: 'flex-start', width: '100%', textTransform: 'none' }, buttonSx]}
        >
          {labelText}
        </Button>
      )}
      <Menu
        id="language-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        MenuListProps={{
          'aria-labelledby': buttonId,
        }}
      >
        <MenuItem onClick={() => handleLanguageChange('de')} selected={language === 'de'}>
          <ListItemIcon>
            <span style={{ fontSize: '1.5rem' }}>🇩🇪</span>
          </ListItemIcon>
          <ListItemText>{t('language.german')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => handleLanguageChange('en')} selected={language === 'en'}>
          <ListItemIcon>
            <span style={{ fontSize: '1.5rem' }}>🇬🇧</span>
          </ListItemIcon>
          <ListItemText>{t('language.english')}</ListItemText>
        </MenuItem>
      </Menu>
    </>
  );
};

export default LanguageSelector;
