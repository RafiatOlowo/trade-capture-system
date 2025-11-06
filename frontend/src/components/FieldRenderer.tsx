import React from "react";
import Input from "./Input";
import Dropdown from "./Dropdown";
import SettlementInstructionField from "./SettlementInstructionField";

/**
 * Props for the FieldRenderer component
 */
export interface FieldRendererProps {
    field: {
        key: string;
        label: string;
        type: string;
        options?: (() => { value: string; label: string }[] | string[]) | { value: string; label: string }[] | string[]
        minLength?: number; 
        maxLength?: number;
        templates?: string[];
    };
    value: string | number | undefined | null;
    disabled: boolean;
    onChange: (e: React.ChangeEvent<HTMLInputElement> | string) => void;
}

/**
 * Renders different types of form fields based on field type
 */
const FieldRenderer: React.FC<FieldRendererProps> = ({field, value, disabled, onChange}) => {
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

    // --- DROPDOWN ---
    if (field.type === "dropdown") {
        const optionsRaw = typeof field.options === 'function' ? field.options() : field.options || [];
        const options = (optionsRaw as ({ value: string; label: string }[] | string[])).map((opt) =>
            typeof opt === 'string' ? { value: opt, label: opt } : opt
        );
        return (
            <Dropdown
                options={options}
                value={typeof value === 'string' || typeof value === 'number' ? value : ''}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => onChange(e.target.value)}
                disabled={disabled}
                className={commonClass}
            />
        );
    }

    // --- SETTLEMENT INSTRUCTIONS (Custom Component) ---
    if (field.type === "settlementInstructionsTemplate") {
        return (
            <SettlementInstructionField
                value={typeof value === 'string' ? value : ""}
                onChange={(val: string) => onChange(val)}
                disabled={disabled}
                className={commonClass}
                minLength={field.minLength}
                maxLength={field.maxLength}
                templates={field.templates}
            />
        );
    }

    // --- INPUT (Default) ---
    return (
        <Input
            size="md"
            value={typeof value === 'string' || typeof value === 'number' ? value.toString() : ""}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
            type={field.type === "date" ? "date" : "text"}
            disabled={disabled}
            className={commonClass}
        />
    );
};

export default FieldRenderer;
