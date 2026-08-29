package Components.Infra;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionPool {
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final Set<Slave> slaves = ConcurrentHashMap.newKeySet();

    public volatile int slavesThatAreCaughtUp = 0;
    public volatile int bytesSentToSlaves = 0;

    public synchronized void slaveAck(int ackResponse) {
        if (this.bytesSentToSlaves == ackResponse) {
            slavesThatAreCaughtUp++;
        }
    }

    public Set<Client> getClients() {
        return clients;
    }

    public Set<Slave> getSlaves() {
        return slaves;
    }

    public void addClient(Client client) {
        if (client != null) {
            clients.add(client);
        }
    }

    public void addSlave(Slave slave) {
        if (slave != null) {
            slaves.add(slave);
        }
    }

    public boolean removeClient(Client client) {
        return clients.remove(client);
    }

    public boolean removeSlave(Slave slave) {
        return slaves.remove(slave);
    }

    public boolean removeSlave(Client client) {
        if (client == null) {
            return false;
        }

        Slave slaveToRemove = null;
        for (Slave slave : slaves) {
            if (slave.connection == client) {
                slaveToRemove = slave;
                break;
            }
        }

        return slaveToRemove != null && slaves.remove(slaveToRemove);
    }
}
