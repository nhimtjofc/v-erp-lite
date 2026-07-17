class PineconeClient:
    """Handles dense vector semantic search for the pruned candidates."""
    
    def __init__(self, api_key="mock_key", environment="us-west1"):
        print(f"Connected to Pinecone Vector DB in {environment}")
        
    def semantic_search(self, query, candidate_ids, top_k=3):
        """
        Performs vector search ONLY on the candidate_ids provided by Elasticsearch.
        This optimizes K-budget in hybrid search.
        """
        print(f"Performing vector search for '{query}' on candidates: {candidate_ids}")
        # Mock vector result
        return [
            {"sku_id": "SHOE-001", "score": 0.95},
        ]
