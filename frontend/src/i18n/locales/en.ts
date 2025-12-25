// English translations

import { TranslationKeys } from './de';

export const en: TranslationKeys = {
  app: {
    title: 'BoxCopilot',
    loading: 'Loading...',
  },
  auth: {
    loginPrompt: 'Please log in to continue',
    loginButton: 'Login with Nextcloud',
    logout: 'Logout',
  },
  boxes: {
    // Labels
    currentRoom: 'Current Room',
    targetRoom: 'Target Room',
    description: 'Description',
    
    // Placeholders
    placeholderCurrentRoom: 'e.g. Living Room',
    placeholderTargetRoom: 'e.g. Basement',
    placeholderDescription: 'Optional box description...',
    
    // Common terms
    box: 'Box',
    boxes: 'Boxes',
    items: 'Items',
    uuid: 'UUID',
    current: 'Current',
    target: 'Target',
    publicLink: 'Public Link',
    
    // Actions
    createNew: 'New Box',
    create: 'Create',
    edit: 'Edit',
    delete: 'Delete',
    save: 'Save',
    saveAndReturn: 'Save and return',
    cancel: 'Cancel',
    back: 'Back',
    close: 'Close',
    reset: 'Reset',
    
    // Box list
    boxNumber: 'Box #{{number}}',
    editBox: 'Edit Box #{{number}}',
    noBoxesFound: 'No boxes found.',
    loadingBoxes: 'Loading boxes...',
    selectBox: 'Select box {{number}}',
    
    // Handling
    transportHints: 'Transport Notes',
    fragile: 'Fragile',
    fragileEmoji: '🔔 Zerbrechlich / Fragile',
    noStack: 'Do Not Stack',
    noStackEmoji: '⛔ Nichts drauf stellen / Do Not Stack',
    
    // Filters
    itemName: 'Item Name',
    itemNamePlaceholder: 'e.g. Hammer',
    targetRoomFilter: 'Target Room',
    targetRoomFilterPlaceholder: 'e.g. Basement',
    advancedFilters: 'Advanced Filters',
    resetFilters: 'Reset Filters',
    onlyFragileBoxes: 'Only fragile boxes',
    onlyNoStackBoxes: 'Only non-stackable boxes',
    
    // Print
    printLabels: 'Print Labels',
    print: 'Print',
    noBoxSelected: 'No box selected.',
    
    // Dialog titles
    createNewBox: 'Create New Box',
    deleteBoxTitle: 'Delete Box?',
    deleteBoxMessage: 'This box will be permanently deleted. This action cannot be undone.',
  },
  items: {
    // Common terms
    item: 'Item',
    items: 'Items',
    name: 'Name',
    actions: 'Actions',
    
    // Actions
    add: 'Add',
    addNew: 'Add New Item',
    edit: 'Edit',
    editItem: 'Edit Item',
    delete: 'Delete',
    deleteItem: 'Delete Item?',
    save: 'Save',
    cancel: 'Cancel',
    
    // Messages
    noItems: 'No items available',
    noItemsInBox: 'No items in this box.',
    deleteMessage: 'This item will be permanently deleted.',
    itemsCount: '{{count}} Items',
  },
  errors: {
    invalidBoxId: 'Invalid Box ID',
    boxNotFound: 'Box not found',
    boxLoadFailed: 'Failed to load box',
    boxSaveFailed: 'Error saving box',
    boxDeleteFailed: 'Box could not be deleted',
    boxCreateFailed: 'Box could not be created',
    boxesFetchFailed: 'Error loading boxes',
    tokenMissing: 'Token missing',
    unknownError: 'Unknown error',
    itemAddFailed: 'Error adding item',
    itemUpdateFailed: 'Error updating item',
    itemDeleteFailed: 'Error deleting item',
    searchFailed: 'Search failed',
    noPublicLink: 'No public link available',
    copyFailed: 'Copy not possible',
    noSearchResults: 'No boxes found for search',
  },
  success: {
    boxSaved: 'Box saved successfully',
    boxCreated: 'Box created',
    boxDeleted: 'Box deleted',
    itemAdded: 'Item added',
    itemUpdated: 'Item updated',
    itemDeleted: 'Item deleted',
    linkCopied: 'Public link copied',
    saving: 'Saving...',
  },
  theme: {
    toggleDarkMode: 'Toggle dark mode',
  },
  language: {
    select: 'Select language',
    german: 'Deutsch',
    english: 'English',
    current: 'Current language',
  },
};
