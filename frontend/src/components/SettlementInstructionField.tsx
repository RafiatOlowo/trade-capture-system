import React, { useState, useEffect } from 'react';
import Dropdown from './Dropdown'; 

// --- Reusable Utility Classes ---
const sizeClasses = {
  sm: 'px-2 py-1 text-sm',
  md: 'px-3 py-2 text-base',
  lg: 'px-4 py-3 text-lg',
};

const variantClasses = {
  primary: 'border-gray-400 focus:border-gray-600 focus:ring-gray-200',
  secondary: 'border-red-600 focus:border-red-700 focus:ring-red-200',
};
// -----------------------------------------------------------------------
interface DropdownOption {
  value: string;
  label: string;
}

interface SettlementInstructionFieldProps extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'size' | 'value' | 'onChange'> {
  // Common Props
  variant?: 'primary' | 'secondary';
  size?: 'sm' | 'md' | 'lg';
  label?: string;
  error?: string;
  disabled?: boolean;

  // Custom Props from TRADE_FIELDS
  minLength?: number; 
  maxLength?: number;
  templates?: string[];
  
  // Value and Handler
  value: string;
  onChange: (value: string) => void;
}

const SettlementInstructionField: React.FC<SettlementInstructionFieldProps> = ({
  variant = 'primary',
  size = 'md',
  label = null,
  error,
  className = '',
  rows = 4,
  value,
  onChange,
  disabled,
  minLength = 0,
  maxLength = 500,
  templates = [],
  ...props
}) => {
  
  // Template Setup
  const templateOptions: DropdownOption[] = templates.map((t, index) => ({
    value: t,
    label: `Template ${index + 1}: ${t.substring(0, 30)}...`
  }));
  
  const [selectedTemplate, setSelectedTemplate] = useState('');

  const handleTemplateChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const templateValue = e.target.value;
    setSelectedTemplate(templateValue);
    
    // Apply the selected template to the main field
    onChange(templateValue);
  };

  // Character Count Logic
  const currentLength = value.length;
  
  const showMinLengthWarning = minLength > 0 && currentLength > 0 && currentLength < minLength;
  const showMaxLengthWarning = currentLength > maxLength;

  // Render
  return (
    <div className="flex flex-col min-w-[185px] p-1 bg-gray-50 rounded-lg border border-indigo-200">
      {/* --- Template Selector --- */}
      {templates.length > 0 && (
        <div className="flex flex-col max-w-[185px] gray-40 gap-1 mb-2">
          <label className="text-sm font-medium whitespace-nowrap">Load Template:</label>
          <Dropdown
            id="template-select"
            name="template-select"
            options={templateOptions}
            value={selectedTemplate}
            onChange={handleTemplateChange}
            disabled={disabled}
            placeholder="Select a template..."
            className="h-8 text-sm" // Smaller style for template dropdown
          />
        </div>
      )}

      {/* --- Textarea --- */}
      <label className="text-sm font-medium">{label}</label>
      <textarea
        placeholder={props.placeholder || "Enter settlement instructions here or select a template above..."}
        className={`border rounded outline-none focus:ring-2 min-w-[185px] min-h-[80px] shadow-2xl text-black transition 
            ${variantClasses[variant]} 
            ${sizeClasses[size]} 
            ${className} ${disabled ? 'bg-gray-100' : 'bg-white'}`}
        rows={rows} 
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        {...props}
      />
      
      {/* --- Character Count & Validation Messages --- */}
      <div className="flex justify-between text-xs mt-1">
        {/* Left Side: Error or Min Length Warning */}
        {error && <span className="text-red-600">{error}</span>}
        {showMinLengthWarning && !error && (
          <span className="text-orange-600">
            Min length: {minLength} characters.
          </span>
        )}
        
        {/* Right Side: Current/Max Count */}
        <span 
          className={`ml-auto ${showMaxLengthWarning ? "text-red-600 font-bold" : "text-gray-500"}`}
        >
          {currentLength} / {maxLength}
        </span>
      </div>
    </div>
  );
};

export default SettlementInstructionField;