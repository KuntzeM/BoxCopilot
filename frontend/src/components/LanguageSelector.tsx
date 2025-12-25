import React, { useState } from 'react';
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material';
import { Language as LanguageIcon } from '@mui/icons-material';
import { useTranslation } from '../hooks/useTranslation';

const LanguageSelector: React.FC = () => {
  const { language, setLanguage, t } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

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
      <IconButton
        onClick={handleClick}
        color="inherit"
        aria-label={t('language.select')}
        aria-controls={open ? 'language-menu' : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        size="medium"
        sx={{ ml: 1 }}
      >
        <LanguageIcon />
      </IconButton>
      <Menu
        id="language-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        MenuListProps={{
          'aria-labelledby': 'language-button',
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
