package leader.client.command;

import leader.client.util.InstanceAccess;

import java.util.ArrayList;

public abstract class Command implements InstanceAccess {
    public final ArrayList<String> names;

    public Command(ArrayList<String> arrayList) {
        this.names = arrayList;
    }

    public abstract void runCommand(ArrayList<String> args);
}
