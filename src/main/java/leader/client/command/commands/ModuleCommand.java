package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.module.Module;
import leader.client.module.values.Value;
import leader.client.module.values.ValueCommands;
import leader.client.module.values.ValueFormat;
import leader.client.module.values.impl.BoolValue;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleCommand extends Command {
    public ModuleCommand() {
        super(new ArrayList<>(Leader.moduleManager.modules.values().stream().<String>map(Module::getName).collect(Collectors.<String>toList())));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        Module module = Leader.moduleManager.getModule(args.get(0));
        if (args.size() >= 2) {
            Value<?> value = ValueCommands.find(module, args.get(1));
            if (value == null) {
                DebugUtil.sendFormatted(String.format("%s%s has no property &o%s&r", Leader.clientName, module.getName(), args.get(1)));
                return;
            }
            handleValue(module, value, args);
        } else {
            List<Value<?>> visibleValues = module.getValues().stream()
                    .filter(Value::canDisplay).collect(Collectors.toList());

            if (!visibleValues.isEmpty()) {
                DebugUtil.sendFormatted(String.format("%s%s:&r", Leader.clientName, module.formatModule()));
                for (Value<?> value : visibleValues) {
                    DebugUtil.sendFormatted(String.format("&7»&r %s: %s&r", value.getInternalName(), ValueFormat.format(value)));
                }
                return;
            }
            DebugUtil.sendFormatted(String.format("%s%s has no properties&r", Leader.clientName, module.formatModule()));
        }
    }

    private void handleValue(Module module, Value<?> value, ArrayList<String> args) {
        if (args.size() < 3 && !(value instanceof BoolValue)) {
            DebugUtil.sendFormatted(
                    String.format(
                            "%s%s: &o%s&r is set to %s&r (%s)&r",
                            Leader.clientName,
                            module.getName(),
                            value.getInternalName(),
                            ValueFormat.format(value),
                            ValueCommands.prompt(value)
                    )
            );
            return;
        }
        String newValue = args.size() < 3 ? null : String.join(" ", args.subList(2, args.size()));
        try {
            if (ValueCommands.parse(value, newValue)) {
                DebugUtil.sendFormatted(
                        String.format("%s%s: &o%s&r has been set to %s&r", Leader.clientName, module.getName(), value.getInternalName(), ValueFormat.format(value))
                );
                return;
            }
        } catch (Exception ignore) {
        }
        DebugUtil.sendFormatted(
                String.format("%sInvalid value for property &o%s&r (%s)&r", Leader.clientName, value.getInternalName(), ValueCommands.prompt(value))
        );
    }
}
