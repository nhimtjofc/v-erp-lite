import React, { useState } from 'react';
import { StreamingClient } from './streaming_client';
import { MarkdownRenderer } from './markdown_renderer';

export const ChatWidget: React.FC = () => {
    const [messages, setMessages] = useState<string[]>([]);
    const [input, setInput] = useState("");

    const handleSend = () => {
        setMessages(prev => [...prev, input]);
        // Mock streaming response
        StreamingClient.streamResponse(input, (chunk) => {
             // In reality, this would append token by token
             console.log("Received chunk:", chunk);
        });
        setInput("");
    };

    return (
        <div className="fixed bottom-4 right-4 w-96 h-128 bg-black/80 backdrop-blur-md rounded-2xl p-4 border border-white/10 text-white shadow-2xl">
            <h3 className="text-xl font-bold mb-4">Enterprise AI Assistant</h3>
            <div className="flex-1 overflow-y-auto mb-4 space-y-4">
                {messages.map((msg, i) => (
                    <div key={i} className="bg-gray-800 p-3 rounded-lg">
                        <MarkdownRenderer content={msg} />
                    </div>
                ))}
            </div>
            <div className="flex gap-2">
                <input 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)} 
                    className="flex-1 bg-gray-900 border border-gray-700 rounded p-2 text-white"
                    placeholder="Ask about your order..."
                />
                <button onClick={handleSend} className="bg-blue-600 px-4 py-2 rounded">Send</button>
            </div>
        </div>
    );
};
