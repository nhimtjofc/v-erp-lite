export class StreamingClient {
    /**
     * Handles Server-Sent Events (SSE) to achieve sub-100ms TTFT.
     */
    static streamResponse(query: string, onChunk: (chunk: string) => void) {
        console.log(`Establishing SSE connection for query: ${query}`);
        
        // Mocking token stream
        const tokens = ["Here ", "is ", "your ", "order ", "status: ", "**SHIPPED**"];
        let delay = 0;
        
        tokens.forEach(token => {
            setTimeout(() => {
                onChunk(token);
            }, delay);
            delay += 50; // 50ms per token latency
        });
    }
}
