import React from 'react';

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
    error?: string;
}

const Textarea: React.FC<TextareaProps> = ({ error, className = '', ...props }) => {
    // Styling classes based on your existing Input component (variantClasses primary)
    const errorClass = error 
        ? 'border-red-600 focus:border-red-700 focus:ring-red-200' 
        : 'border-gray-400 focus:border-gray-600 focus:ring-gray-200';
        
    return (
        <textarea
            className={`w-full p-3 text-sm border rounded outline-none focus:ring-2 shadow-inner transition resize-none min-w-[185px] text-black ${errorClass} ${className}`}
            {...props}
        />
    );
};

export default Textarea;
