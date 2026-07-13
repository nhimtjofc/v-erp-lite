import json

class SemanticChunker:
    """Chunks product data into Parent-Child structure for Agentic RAG."""
    
    def __init__(self):
        pass
        
    def chunk_product(self, product_data):
        """
        Creates a parent document and child variants to preserve hierarchical structures.
        """
        parent_chunk = {
            "type": "parent",
            "doc_id": product_data["sku_id"],
            "content": f"{product_data.get('name')} - Premium footwear.",
            "metadata": {
                "sku": product_data["sku_id"],
                "price": product_data.get("price")
            }
        }
        
        # Mocking variant iteration
        child_chunk = {
            "type": "child",
            "parent_id": product_data["sku_id"],
            "content": "Variant: Red, Size 10",
            "metadata": {
                "color": "Red",
                "size": 10,
                "in_stock": True
            }
        }
        return [parent_chunk, child_chunk]
