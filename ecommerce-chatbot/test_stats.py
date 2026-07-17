import os
import sys
from dotenv import load_dotenv
load_dotenv()
sys.path.append(os.getcwd())
from agents.stats_agent import StatsAgent
agent = StatsAgent()
print(agent.handle_query("c?a hàng hi?n t?i có bao nhiu s?n ph?m"))
