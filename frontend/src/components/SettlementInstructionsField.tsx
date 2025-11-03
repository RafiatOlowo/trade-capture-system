import React, { useMemo, useCallback, useState } from 'react';
import Textarea from './Textarea';
import { TemplateItem } from '../stores/staticStore';

export type SettlementFieldConfig = {
    minLength: number;
    maxLength: number;
    placeholder: string;
    templates: TemplateItem[];
}

interface SettlementInstructionsProps {
    fieldConfig: SettlementFieldConfig & {
        key: string;
        label: string;
        type: string;
        optional?: boolean; 
    };
    value: string;
    onChange: (key: string, value: string) => void; 
}

/**
 * Basic client-side check for potentially unsafe characters or SQL keywords.
 */
const isSqlInjectionRisky = (text: string): boolean => {
    // Check for common SQL injection keywords and separators
    const sqlPatterns = /(SELECT\s|DROP\s|INSERT\s|UPDATE\s|DELETE\s|;|\-\-|\' OR \'1\'=\'1)/i;
    return sqlPatterns.test(text);
};


const SettlementInstructionsField: React.FC<SettlementInstructionsProps> = ({
    fieldConfig,
    value,
    onChange
}) => {
    const { key, minLength, maxLength, placeholder, templates } = fieldConfig;
    
    // State to hold the content of the template selected, if overwrite is pending
    const [pendingTemplateContent, setPendingTemplateContent] = useState<string | null>(null);
    const [templateValue, setTemplateValue] = useState<string>('');
    const id = `field-${key}`;
    const showConfirm = !!pendingTemplateContent;

    // --- Validation Logic (10-500 characters, optional) ---
    const error = useMemo(() => {
        const trimmedValue = value.trim();
        if (trimmedValue.length === 0) {
            return undefined; // Optional field is empty, which is valid.
        }

        if (trimmedValue.length < minLength) {
            return `Must be at least ${minLength} characters.`;
        }
        if (trimmedValue.length > maxLength) {
            return `Exceeds maximum length of ${maxLength} characters.`;
        }
        if (isSqlInjectionRisky(trimmedValue)) {
            return "Unsafe content detected. Please remove special characters or keywords.";
        }
        return undefined;
    }, [value, minLength, maxLength, fieldConfig.optional]);

    // Function to apply the template immediately or set up confirmation
    const handleTemplateSelect = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
        const selectedName = e.target.options[e.target.selectedIndex].text; // Optional: grab the name for display

        setTemplateValue(selectedName); // Still necessary to control the dropdown selection
        
        // Lookup the template content from the config array
        const template = templates.find(t => t.name === selectedName);
        
        if (!template) {
             console.error(`[CRITICAL ERROR] Template not found for name: ${selectedName}`);
             setTemplateValue('');
             return;
        }

        const contentToApply = template.content;

        if (value.trim().length > 10) {
            setPendingTemplateContent(contentToApply);
        } else {
            onChange(key, contentToApply);
            setTemplateValue(''); // Clear dropdown value after applying
        }
    }, [key, value, onChange, templates]);

    // Final confirmation action
    const handleConfirmOverwrite = useCallback(() => {
        if (pendingTemplateContent !== null) {
            onChange(key, pendingTemplateContent);
        }
        setPendingTemplateContent(null);
        setTemplateValue(''); // Reset dropdown
    }, [key, onChange, pendingTemplateContent]);

    // Cancellation action
    const handleCancelOverwrite = useCallback(() => {
        setPendingTemplateContent(null);
        setTemplateValue(''); // Reset dropdown
    }, []);

    return (
        <div className="w-full max-w-lg flex flex-col p-4 bg-gray-50 rounded-lg border border-indigo-200 shadow-xl">
            
            {/* Header and Template Dropdown */}
            <div className="flex items-center justify-between mb-2">
                <div className="text-base font-bold text-gray-800">
                    {/* Display Status or Error */}
                    {error ? (
                        <span className="text-red-600 text-sm font-normal">Validation Error: ({error})</span>
                    ) : (
                        // Removed "Settlement Instructions (Optional)" header text.
                        null
                    )}
                </div>
                
                {templates.length > 0 && (
                    <div className="relative">
                         <select
                            id={`${id}-template`}
                            value={templateValue}
                            onChange={handleTemplateSelect}
                            // Disabled when confirmation is pending
                            disabled={showConfirm}
                            className={`text-xs appearance-none border rounded-lg pl-2 pr-6 py-1 cursor-pointer transition-colors ${showConfirm ? 'bg-gray-200 text-gray-500' : 'bg-indigo-50 border-indigo-300 hover:bg-indigo-100'}`}
                         >
                            <option value="" disabled>Load Template...</option>
                            {templates.map((t) => (
                                <option key={t.name} value={t.content}>
                                    {t.name}
                                </option>
                            ))}
                         </select>
                         {/* Inline SVG replacement for ChevronDown */}
                         <svg 
                            className="absolute right-1 top-1/2 transform -translate-y-1/2 h-3 w-3 text-indigo-500 pointer-events-none" 
                            xmlns="http://www.w3.org/2000/svg" 
                            viewBox="0 0 24 24" 
                            fill="none" 
                            stroke="currentColor" 
                            strokeWidth="2" 
                            strokeLinecap="round" 
                            strokeLinejoin="round"
                         >
                            <polyline points="6 9 12 15 18 9"></polyline>
                         </svg>
                    </div>
                )}
            </div>
            
            {/* Template Overwrite Confirmation UI */}
            {showConfirm && (
                <div className="bg-yellow-100 border-l-4 border-yellow-500 text-yellow-800 p-2 mb-2 rounded-md flex items-center justify-between text-sm">
                    <span>Warning: Overwrite existing instructions?</span>
                    <div className="flex space-x-2 ml-4">
                        <button 
                            onClick={handleConfirmOverwrite} 
                            className="px-3 py-1 bg-red-500 text-white rounded hover:bg-red-600 transition-colors shadow-sm"
                        >
                            Overwrite
                        </button>
                        <button 
                            onClick={handleCancelOverwrite} 
                            className="px-3 py-1 bg-gray-300 text-gray-800 rounded hover:bg-gray-400 transition-colors shadow-sm"
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}

            {/* The Textarea */}
            <Textarea
                id={id}
                value={value}
                onChange={(e) => onChange(key, e.target.value)}
                placeholder={placeholder}
                rows={5} // Maintains the different height
                maxLength={maxLength}
                error={error}
                // Disable textarea when confirmation is pending
                disabled={showConfirm}
            />

            {/* Character Count for user guidance */}
            <div className="mt-1 text-xs text-right text-gray-500">
                {value.length} / {maxLength} characters
            </div>
        </div>
    );
};

export default SettlementInstructionsField;