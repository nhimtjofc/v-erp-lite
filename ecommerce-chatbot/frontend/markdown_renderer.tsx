import React from 'react';

interface Props {
    content: string;
}

export const MarkdownRenderer: React.FC<Props> = ({ content }) => {
    /**
     * intercepts standard markdown and renders interactive UI cards.
     * e.g., turning a markdown table into a Glassmorphic Product Card.
     */
    
    // Mock simple rendering
    const isTable = content.includes("|");
    
    if (isTable) {
        return (
            <div className="border border-blue-500/30 bg-blue-900/20 p-4 rounded-xl backdrop-blur-sm">
                <p className="text-blue-300 font-semibold mb-2">Interactive Product Card</p>
                <div dangerouslySetInnerHTML={{ __html: content }} />
            </div>
        );
    }

    return <div dangerouslySetInnerHTML={{ __html: content }} />;
};
