import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from agents.retrieval_agent import RetrievalAgent

agent = RetrievalAgent()
res = agent.handle_query('Có bán iPhone không')
print(res)
