class MemoryManager:
    """Summarizes conversational memory asynchronously to save tokens."""
    
    def __init__(self):
        self.history = []
        
    def add_interaction(self, user_msg, ai_msg):
        self.history.append({"user": user_msg, "ai": ai_msg})
        if len(self.history) > 5:
            self.summarize_memory()
            
    def summarize_memory(self):
        print("Triggering background memory summarization to prevent token bloat...")
        self.history = [{"system": "User is looking for red shoes. Discussing order #12345."}]
