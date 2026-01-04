import React, { useState } from 'react';
import { Item } from '../types';

interface ItemFormProps {
  onSubmit: (item: Omit<Item, 'id'>) => void;
  initialItem?: Item;
}

export const ItemForm: React.FC<ItemFormProps> = ({ onSubmit, initialItem }) => {
  const [name, setName] = useState(initialItem?.name || '');
  const [description, setDescription] = useState(initialItem?.description || '');
  const [quantity, setQuantity] = useState(initialItem?.quantity || 1);
  const [category, setCategory] = useState(initialItem?.category || '');
  const [tags, setTags] = useState(initialItem?.tags?.join(', ') || '');
  const [imagePreview, setImagePreview] = useState<string | null>(initialItem?.image || null);
  const [imageFile, setImageFile] = useState<File | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    const item: Omit<Item, 'id'> = {
      name,
      description,
      quantity,
      category,
      tags: tags.split(',').map(tag => tag.trim()).filter(Boolean),
      image: imagePreview || undefined,
      boxId: initialItem?.boxId
    };

    onSubmit(item);
    
    if (!initialItem) {
      setName('');
      setDescription('');
      setQuantity(1);
      setCategory('');
      setTags('');
      setImagePreview(null);
      setImageFile(null);
    }
  };

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        alert('Image size should be less than 5MB');
        return;
      }
      
      if (!file.type.startsWith('image/')) {
        alert('Please select a valid image file');
        return;
      }

      setImageFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        const result = reader.result as string;
        setImagePreview(result);
      };
      reader.readAsDataURL(file);
    } else {
      const preview = URL.createObjectURL(file);
      if (preview.startsWith('blob:')) {
        setImagePreview(preview);
      }
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 bg-white p-6 rounded-lg shadow-md max-w-2xl mx-auto">
      <h2 className="text-2xl font-bold text-gray-800 mb-6">
        {initialItem ? 'Edit Item' : 'Add New Item'}
      </h2>

      <div className="form-group">
        <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-2">
          Item Name *
        </label>
        <input
          id="name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          placeholder="Enter item name"
        />
      </div>

      <div className="form-group">
        <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-2">
          Description
        </label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent min-h-[100px]"
          placeholder="Enter item description"
        />
      </div>

      <div className="form-group">
        <label htmlFor="quantity" className="block text-sm font-medium text-gray-700 mb-2">
          Quantity *
        </label>
        <input
          id="quantity"
          type="number"
          value={quantity}
          onChange={(e) => setQuantity(parseInt(e.target.value))}
          min="1"
          required
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
      </div>

      <div className="form-group">
        <label htmlFor="category" className="block text-sm font-medium text-gray-700 mb-2">
          Category
        </label>
        <input
          id="category"
          type="text"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          placeholder="e.g., Electronics, Books, Clothing"
        />
      </div>

      <div className="form-group">
        <label htmlFor="tags" className="block text-sm font-medium text-gray-700 mb-2">
          Tags
        </label>
        <input
          id="tags"
          type="text"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          placeholder="Enter tags separated by commas"
        />
      </div>

      <div className="form-group">
        <label htmlFor="image" className="block text-sm font-medium text-gray-700 mb-2">
          Item Image
        </label>
        <input
          id="image"
          type="file"
          accept="image/*"
          onChange={handleImageChange}
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
        />
        {imagePreview && (
          <div className="mt-4">
            <img 
              src={imagePreview} 
              alt="Preview" 
              className="max-w-full h-auto max-h-64 rounded-md shadow-sm"
            />
          </div>
        )}
      </div>

      <button
        type="submit"
        className="w-full bg-blue-600 text-white py-3 px-6 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 font-medium transition-colors"
      >
        {initialItem ? 'Update Item' : 'Add Item'}
      </button>
    </form>
  );
};