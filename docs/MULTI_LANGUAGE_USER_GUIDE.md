# Multi-Language Support - User Guide

## 🌍 Overview

BoxCopilot now supports multiple languages! You can switch between German (Deutsch) and English at any time, and your language preference will be saved automatically.

## 🎯 How to Use

### Changing the Language

1. **Look for the Language Icon** 🌐
   - Located in the top navigation bar (AppBar)
   - Between the theme toggle (sun/moon icon) and the logout button

2. **Click the Language Icon**
   - A dropdown menu will appear

3. **Select Your Language**
   - 🇩🇪 **Deutsch** - German (default)
   - 🇬🇧 **English** - English

4. **Done!**
   - The entire interface will immediately switch to your chosen language
   - Your preference is automatically saved and will persist across sessions

## 📱 Where Language Changes Apply

The language setting affects **all** text in the application:

### Login & Authentication
- Login prompts
- Login button
- Error messages

### Box Management
- Box creation and editing forms
- Field labels (Current Room, Target Room, Description)
- Transport hints (Fragile, Do Not Stack)
- All buttons (Save, Cancel, Delete, etc.)

### Item Management
- Item creation and editing
- Item list and table headers
- All dialogs and confirmations

### Search & Filters
- Search field labels
- Filter options
- Advanced filter toggles

### Messages & Notifications
- Success messages
- Error messages
- Loading indicators

### Public Preview
- Public box view (accessible without login)
- All labels and descriptions

### Print Labels
- Print dialog
- QR code labels
- All text on printed labels

## 💾 Language Persistence

Your language choice is saved in a cookie that lasts for **365 days**. This means:
- Your language preference is remembered between visits
- No need to re-select your language each time
- Works across different devices (if using the same browser)

## 🔧 Technical Details

### Cookie Information
- **Cookie Name**: `app-language`
- **Valid Values**: `de` (German) or `en` (English)
- **Expiry**: 365 days
- **Storage**: Browser cookies (same location as theme preference)

### Default Language
- **German (Deutsch)** is the default language
- If you clear your browser cookies, the language will reset to German

## 🌟 Features

### Type-Safe Translations
- All translations are type-checked at compile time
- Ensures consistency across languages
- Prevents missing translations

### Instant Switching
- No page reload required
- Changes apply immediately
- Smooth transition between languages

### Comprehensive Coverage
- Every user-facing text is translated
- Consistent terminology
- Professional translations in both languages

## 🆘 Troubleshooting

### Language doesn't change
- Clear your browser cache and cookies
- Reload the page
- Try selecting the language again

### Language resets to German
- Check if cookies are enabled in your browser
- Verify that cookies aren't being automatically deleted
- Check browser privacy settings

### Some text is not translated
- This shouldn't happen! If you find untranslated text, please report it as a bug

## 🔮 Future Languages

The system is designed to easily support additional languages. Future additions might include:
- French (Français)
- Spanish (Español)
- Italian (Italiano)
- And more...

## 📋 Translation Quality

All translations have been carefully crafted to ensure:
- Natural language flow
- Appropriate terminology for moving/logistics context
- Consistency across the application
- Professional tone

## 🎨 Visual Guide

### Language Selector Location
```
┌─────────────────────────────────────────────────────┐
│ 📦 BoxCopilot    🌙  🌐  [Logout] │
└─────────────────────────────────────────────────────┘
                    ↑
              Language Icon
```

### Language Menu
```
┌──────────────────┐
│ 🇩🇪 Deutsch      │ ← Currently selected
│ 🇬🇧 English      │
└──────────────────┘
```

## 💡 Tips

1. **Try both languages** to see which you prefer
2. **Language is independent** of the theme (light/dark mode)
3. **Share links** with others - they can choose their own language
4. **Print labels** will use the currently selected language
5. **Public links** allow language selection for viewers (no login required)

## 📞 Support

If you encounter any issues with language support:
1. Check this guide for solutions
2. Try clearing browser cache
3. Report persistent issues to the development team

---

**Enjoy BoxCopilot in your preferred language! 🎉**
