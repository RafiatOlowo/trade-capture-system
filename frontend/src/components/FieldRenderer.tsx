import React from "react";
import Input from "./Input";
import Dropdown from "./Dropdown";
import Textarea from "./Textarea";
import SettlementInstructionsField, { SettlementFieldConfig } from "./SettlementInstructionsField";

/**
 * Define the structure of the properties specific to the Settlement Instructions field
 * that are placed directly on the field object.
 */
interface SettlementFieldProperties {
    minLength: number;
    maxLength: number;
    placeholder: string;
}

/**
 * Props for the FieldRenderer component
 */
export interface FieldRendererProps {
    field: {
        key: string;
        label: string;
        type: string;
        // Generic options for dropdowns/selects.
        options?: (() => any[]) | any[]; 
        optional?: boolean;
    } & Partial<SettlementFieldProperties>; // Merge the optional static properties onto the field object
    
    value: string | number | undefined | null;
    disabled: boolean;
   
    // Polymorphic handler:
    // 1. Simple fields: onChange(newValue)
    // 2. Specialized fields (like Settlement): onChange(key, newValue)
    onChange: (keyOrValue: any, value?: string) => void;
}

/**
 * Renders different types of form fields based on field type
 */
const FieldRenderer: React.FC<FieldRendererProps> = ({field, value, disabled, onChange}) => {
    
    // Convert the generic onChange signature to a unified handler for simple fields
    const handleGenericChange = (newValue: string) => {
        // When calling onChange with one argument, it represents the new value.
        // The first argument of the generic signature handles this.
        onChange(newValue); 
    };

    // Special styling for trade status field
    const getFieldClass = () => {
        if (field.key === "tradeStatus") {
            if (value === "TERMINATED") {
                return "bg-red-600 text-white";
            } else if (value) {
                return "bg-green-500 text-white";
            }
        }
        // Default styling for other fields
        return disabled ? "bg-gray-200" : "bg-white";
    };

    const commonClass = `h-9 px-2 py-1 text-sm ${getFieldClass()}`;

    // CONDITIONAL RENDERING FOR THE SPECIALIZED FIELD
    if (field.key === "settlementInstructions") {
        
        // Resolve the templates from the options function/array
        const templates = (typeof field.options === 'function' ? field.options() : field.options || []) as any[];
        
        // Basic validation for the required static properties
        if (typeof field.minLength !== 'number' || typeof field.maxLength !== 'number' || !field.placeholder) {
            return <div className="p-2 text-red-500 rounded-lg bg-red-50 border border-red-200">Error: Missing required static configuration for Settlement Instructions field (e.g., minLength, maxLength, or placeholder).</div>;
        }

        // Define the target shape which is the merged static config + dynamic field properties
        // Omit the optional property from the initial spread type to prevent the boolean | undefined conflict
        type BaseFieldPropsForSpread = Omit<FieldRendererProps['field'], 'optional'> & { optional?: boolean };
        
        type FullSettlementConfig = SettlementFieldConfig & BaseFieldPropsForSpread;

        // Construct the full config object.
        const settlementConfig: FullSettlementConfig = {
            // Asserting all required SettlementFieldProperties exist after validation
            ...field as Required<SettlementFieldProperties> & BaseFieldPropsForSpread,
            templates: templates, 
        };

        // The specialized component handles its own prominence, styling, and validation.
        return (
            <SettlementInstructionsField
                fieldConfig={settlementConfig}
                value={typeof value === 'string' ? value : ''}
                // CASTING is necessary here to satisfy the specialized component's two-parameter signature.
                onChange={onChange as (key: string, value: string) => void}
            />
        );
    }
    
    // Dropdown rendering path
    if (field.type === "dropdown") {
        const optionsRaw = typeof field.options === 'function' ? field.options() : field.options || [];
        const options = (optionsRaw as ({ value: string; label: string }[] | string[])).map((opt) =>
            typeof opt === 'string' ? { value: opt, label: opt } : opt
        );
        return (
            <Dropdown
                options={options}
                value={typeof value === 'string' || typeof value === 'number' ? value : ''}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => handleGenericChange(e.target.value)}
                disabled={disabled}
                className={commonClass}
            />
        );
    }

    // Textarea rendering path
    if (field.type === "textarea") {
        return (
            <Textarea
                value={typeof value === 'string' || typeof value === 'number' ? value.toString() : ""}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => handleGenericChange(e.target.value)}
                disabled={disabled}
                className={`py-1 text-sm rounded w-full ${getFieldClass()}`}
            />
        );
    }

    // Input rendering path
    return (
        <Input
            size="md"
            value={typeof value === 'string' || typeof value === 'number' ? value.toString() : ""}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleGenericChange(e.target.value)}
            type={field.type === "date" ? "date" : "text"}
            disabled={disabled}
            className={commonClass}
        />
    );
};

export default FieldRenderer;
