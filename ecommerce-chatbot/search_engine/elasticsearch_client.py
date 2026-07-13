class ElasticsearchClient:
    """Handles deterministic exact-match queries for hard constraints."""
    
    def __init__(self, host="localhost", port=9200):
        self.host = host
        self.port = port
        print(f"Connected to Elasticsearch at {host}:{port}")
        
    def filter_candidates(self, filters):
        """
        Filters SKUs based on constraints like size, color, price.
        Returns a list of valid SKU IDs.
        """
        print(f"Executing ES Filter Query with constraints: {filters}")
        # Mocking an ES query execution, filtering 150k SKUs down to valid ones
        return ["SHOE-001", "SHOE-002"]
