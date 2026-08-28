package server;

import agent.Agent;
import java.util.ArrayList;
import java.util.List;

public class AgentManager {
    private final Database db;

    public AgentManager(Database db) {
        this.db = db;
    }

    public List<Agent> listAgents() {
        if (db == null) {
            return new ArrayList<>();
        }
        return db.listAgents();
    }

    public Agent getAgent(String agentId) {
        if (db == null) {
            return null;
        }
        return db.getAgent(agentId);
    }

    public List<Agent> listGroupAgents(int groupId) {
        if (db == null) {
            return new ArrayList<>();
        }
        return db.listGroupAgents(groupId);
    }

    public boolean addGroupAgent(String requester, int groupId, String agentId) {
        if (db == null || !db.isGroupOwner(groupId, requester)) {
            return false;
        }
        return db.addGroupAgent(groupId, agentId, requester);
    }

    public boolean removeGroupAgent(String requester, int groupId, String agentId) {
        if (db == null || !db.isGroupOwner(groupId, requester)) {
            return false;
        }
        return db.removeGroupAgent(groupId, agentId);
    }

    public boolean isGroupAgent(int groupId, String agentId) {
        return db != null && db.isGroupAgent(groupId, agentId);
    }

    public void logAgentMessage(int groupId, String agentId, String prompt, String response, String confidence) {
        if (db != null) {
            db.logAgentMessage(groupId, agentId, prompt, response, confidence);
        }
    }
}
