package carpet.script.command;

import carpet.fakes.BlockStateArgumentInterface;
import carpet.script.CarpetScriptHost;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.BlockValue;
import carpet.script.value.EntityValue;
import carpet.script.value.ListValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import carpet.script.value.ValueConversions;
import com.google.common.collect.Lists;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.AngleArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.RotationArgumentType;
import net.minecraft.command.argument.SwizzleArgumentType;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;


public abstract class CommandArgument
{
    private static final List<? extends CommandArgument> baseTypes = Lists.newArrayList(
            // default
            new StringArgument(),
            // vanilla arguments as per https://minecraft.gamepedia.com/Argument_types
            new VanillaUnconfigurableArgument( "bool",
                    BoolArgumentType::bool, (c, p) -> new NumericValue(BoolArgumentType.getBool(c, p)), false
            ),
            new FloatArgument(),
            new IntArgument(),
            new WordArgument(), new GreedyStringArgument(),
            new VanillaUnconfigurableArgument( "yaw",  // angle
                    AngleArgumentType::angle, (c, p) -> new NumericValue(AngleArgumentType.getAngle(c, p)), true
            ),
            new BlockPosArgument(),
            new VanillaUnconfigurableArgument( "block",
                    BlockStateArgumentType::blockState,
                    (c, p) -> {
                        BlockStateArgument result = BlockStateArgumentType.getBlockState(c, p);
                        return new BlockValue(result.getBlockState(), null, null, ((BlockStateArgumentInterface)result).getCMTag() );
                    },
                    false
            ),
            // block_predicate todo - not sure about the returned format. Needs to match block tags used in the API (future)
            new VanillaUnconfigurableArgument("color",
                    ColorArgumentType::color,
                    (c, p) -> {
                        Formatting format = ColorArgumentType.getColor(c, p);
                        return ListValue.of(StringValue.of(format.getName()), new NumericValue(format.getColorValue()*256+255));
                    },
                    false
            ),
            new VanillaUnconfigurableArgument("columnpos",
                    ColumnPosArgumentType::columnPos, (c, p) -> ValueConversions.of(ColumnPosArgumentType.getColumnPos(c, p)), false
            ),
            // component  // raw json
            new VanillaUnconfigurableArgument("dimension",
                    DimensionArgumentType::dimension, (c, p) -> ValueConversions.of(DimensionArgumentType.getDimensionArgument(c, p)), false
            ),
            new EntityArgument(),
            // entity
            // anchor // entity_anchor
            // entitytype       // entity_summon
            // floatrange
            // function??
            // player  // game_profile
            // intrange
            // enchantment
            // item_predicate  ?? //same as item but accepts tags, not sure right now
            // slot // item_slot
            // item  // no tags item_stack
            // message ?? isn't it just because you can't do shit with texts in vanilla?
            // effect // mob_effect
            // tag // for nbt_compound_tag and nbt_tag
            // nbtpath
            // objective
            // criterion
            // operation // not sure if we need it, you have scarpet for that
            // particle
            // resource ??
            new VanillaUnconfigurableArgument("rotation",
                    RotationArgumentType::rotation,
                    (c, p) -> {
                        Vec2f rot = RotationArgumentType.getRotation(c, p).toAbsoluteRotation(c.getSource());
                        return ListValue.of(new NumericValue(rot.x), new NumericValue(rot.y));
                    },
                    true
            ),
            // scoreholder
            // scoreboardslot
            new VanillaUnconfigurableArgument("swizzle",
                    SwizzleArgumentType::swizzle, (c, p) -> StringValue.of(SwizzleArgumentType.getSwizzle(c, p).stream().map(Direction.Axis::asString).collect(Collectors.joining())), true
            ),
            // team
            new VanillaUnconfigurableArgument("time",
                    TimeArgumentType::time, (c, p) -> new NumericValue(IntegerArgumentType.getInteger(c, p)), false
            ),
            // uuid
            // columnlocation // vec2
            new LocationArgument()
            // location // vec3
    );

    public static final Map<String, CommandArgument> builtIns = baseTypes.stream().collect(Collectors.toMap(CommandArgument::getTypeSuffix, a -> a));

    public static final CommandArgument DEFAULT = baseTypes.get(0);

    private static CommandArgument getTypeForArgument(String argument, CarpetScriptHost host)
    {
        String[] components = argument.split("_");
        String suffix = components[components.length-1].toLowerCase(Locale.ROOT);
        CommandArgument arg =  host.appArgTypes.get(suffix);
        if (arg != null) return arg;
        return builtIns.getOrDefault(suffix, DEFAULT);
    }

    public static RequiredArgumentBuilder<ServerCommandSource, ?> argumentNode(String param, CarpetScriptHost host)
    {
        CommandArgument arg = getTypeForArgument(param, host);
        return arg.needsMatching? argument(param, arg.getArgumentType()).suggests(arg::suggest) : argument(param, arg.getArgumentType());
    }

    protected String suffix;
    protected Collection<String> examples;
    protected boolean needsMatching = false;

    protected CommandArgument(
            String suffix,
            Collection<String> examples,
            boolean needsMatching)
    {
        this.suffix = suffix;
        this.examples = examples;
        this.needsMatching = needsMatching;
    }

    public static ArgumentType<?> getArgument(String param, CarpetScriptHost host)
    {
        return getTypeForArgument(param, host).getArgumentType();
    }

    protected abstract ArgumentType<?> getArgumentType();


    public static Value getValue(CommandContext<ServerCommandSource> context, String param, CarpetScriptHost host) throws CommandSyntaxException
    {
        return getTypeForArgument(param, host).getValueFromContext(context, param);
    }

    protected abstract Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException;

    public String getTypeSuffix()
    {
        return suffix;
    }

    public static CommandArgument buildFromConfig(String suffix, Map<String, Value> config)
    {
        if (!config.containsKey("type"))
            throw new InternalExpressionException("Custom types should at least specify the base type");
        String baseType = config.get("type").getString();
        if (!builtIns.containsKey(baseType))
            throw new InternalExpressionException("Unknown base type: "+baseType);
        CommandArgument variant = builtIns.get(baseType).builder().get();
        variant.configure(config);
        variant.suffix = suffix;
        return variant;
    }

    protected void configure(Map<String, Value> config)
    {
        if (config.containsKey("suggest"))
        {
            Value suggestionValue = config.get("suggest");
            if (!(suggestionValue instanceof ListValue)) throw new InternalExpressionException("Argument suggestions needs to be a list");
            examples = ((ListValue) suggestionValue).getItems().stream()
                    .map(Value::getString)
                    .collect(Collectors.toSet());
            if (!examples.isEmpty()) needsMatching = true;
        }
    };

    public CompletableFuture<Suggestions> suggest(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder suggestionsBuilder
    )
    {
        String prefix = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
        suggestFor(prefix).forEach(suggestionsBuilder::suggest);
        return suggestionsBuilder.buildFuture();
    }

    protected List<String> suggestFor(String prefix)
    {
        return getOptions().stream().filter(s -> optionMatchesPrefix(prefix, s)).collect(Collectors.toList());
    }

    protected Collection<String> getOptions()
    {
        //return Lists.newArrayList("");
        // better than nothing I guess
        // nothing is such a bad default.
        if (needsMatching) return examples;
        return Collections.singletonList("... "+getTypeSuffix());
    }

    protected boolean optionMatchesPrefix(String prefix, String option)
    {
        for(int i = 0; !option.startsWith(prefix, i); ++i)
        {
            i = option.indexOf('_', i);
            if (i < 0) return false;
        }
        return true;
    }

    protected abstract Supplier<CommandArgument> builder();

    private static class StringArgument extends CommandArgument
    {
        Set<String> validOptions = Collections.emptySet();
        boolean caseSensitive = false;
        private StringArgument()
        {
            super("string", StringArgumentType.StringType.QUOTABLE_PHRASE.getExamples(), true);
        }

        @Override
        public ArgumentType<?> getArgumentType()
        {
            return StringArgumentType.string();
        }

        @Override
        public Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            String choseValue = StringArgumentType.getString(context, param);
            if (!caseSensitive) choseValue = choseValue.toLowerCase(Locale.ROOT);
            if (!validOptions.isEmpty() && !validOptions.contains(choseValue))
            {
                throw new SimpleCommandExceptionType(new LiteralText("Incorrect value for "+param+": "+choseValue)).create();
            }
            return StringValue.of(choseValue);
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            caseSensitive = config.getOrDefault("case_sensitive", Value.FALSE).getBoolean();
            if (config.containsKey("options"))
            {
                Value optionsValue = config.get("options");
                if (!(optionsValue instanceof ListValue)) throw new InternalExpressionException("Custom sting type requires options passed as a list");
                validOptions = ((ListValue) optionsValue).getItems().stream()
                        .map(v -> caseSensitive?v.getString():v.getString().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
            }
        }

        @Override
        protected Collection<String> getOptions() { return validOptions.isEmpty()?super.getOptions():validOptions; }

        @Override
        protected Supplier<CommandArgument> builder() { return WordArgument::new; }
    }

    private static class WordArgument extends StringArgument
    {
        private WordArgument() { super(); suffix = "term"; examples = StringArgumentType.StringType.SINGLE_WORD.getExamples(); }
        @Override
        public ArgumentType<?> getArgumentType() { return StringArgumentType.word(); }
        @Override
        protected Supplier<CommandArgument> builder() { return WordArgument::new; }
    }

    private static class GreedyStringArgument extends StringArgument
    {
        private GreedyStringArgument() { super();suffix = "text"; examples = StringArgumentType.StringType.GREEDY_PHRASE.getExamples(); }
        @Override
        public ArgumentType<?> getArgumentType() { return StringArgumentType.greedyString(); }
        @Override
        protected Supplier<CommandArgument> builder() { return GreedyStringArgument::new; }
    }

    private static class BlockPosArgument extends CommandArgument
    {
        private boolean mustBeLoaded = false;

        private BlockPosArgument()
        {
            super("pos", BlockPosArgumentType.blockPos().getExamples(), false);
        }

        @Override
        public ArgumentType<?> getArgumentType()
        {
            return BlockPosArgumentType.blockPos();
        }

        @Override
        public Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            BlockPos pos = mustBeLoaded
                    ? BlockPosArgumentType.getLoadedBlockPos(context, param)
                    : BlockPosArgumentType.getBlockPos(context, param);
            return ValueConversions.of(pos);
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            mustBeLoaded = config.getOrDefault("loaded", Value.FALSE).getBoolean();
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return BlockPosArgument::new;
        }
    }

    private static class LocationArgument extends CommandArgument
    {
        boolean blockCentered;

        private LocationArgument()
        {
            super("location", Vec3ArgumentType.vec3().getExamples(), false);
            blockCentered = true;
        }
        @Override
        protected ArgumentType<?> getArgumentType()
        {
            return Vec3ArgumentType.vec3(blockCentered);
        }

        @Override
        protected Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            return ValueConversions.of(Vec3ArgumentType.getVec3(context, param));
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            blockCentered = config.getOrDefault("block_centered", Value.TRUE).getBoolean();
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return LocationArgument::new;
        }
    }

    private static class EntityArgument extends CommandArgument
    {
        boolean onlyFans;
        boolean single;

        private EntityArgument()
        {
            super("entity", EntityArgumentType.entities().getExamples(), false);
            onlyFans = false;
            single = false;
        }
        @Override
        protected ArgumentType<?> getArgumentType()
        {
            if (onlyFans)
            {
                return single?EntityArgumentType.player():EntityArgumentType.players();
            }
            else
            {
                return single?EntityArgumentType.entity():EntityArgumentType.entities();
            }
        }

        @Override
        protected Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            List<? extends Entity> founds = (List)EntityArgumentType.getOptionalEntities(context, param);
            if (single)
            {
                return founds.isEmpty()?Value.NULL:new EntityValue(founds.get(0));
            }
            else
            {
                return ListValue.wrap(founds.stream().map(EntityValue::new).collect(Collectors.toList()));
            }
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            onlyFans = config.getOrDefault("players", Value.FALSE).getBoolean();
            single = config.getOrDefault("single", Value.FALSE).getBoolean();
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return EntityArgument::new;
        }
    }

    private static class FloatArgument extends CommandArgument
    {
        private Double min = null;
        private Double max = null;
        private FloatArgument()
        {
            super("float", DoubleArgumentType.doubleArg().getExamples(), true);
        }

        @Override
        public ArgumentType<?> getArgumentType()
        {
            if (min != null)
            {
                if (max != null)
                {
                    return DoubleArgumentType.doubleArg(min, max);
                }
                return DoubleArgumentType.doubleArg(min);
            }
            return DoubleArgumentType.doubleArg();
        }

        @Override
        public Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            return new NumericValue(DoubleArgumentType.getDouble(context, param));
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            if (config.containsKey("min"))
            {
                min = NumericValue.asNumber(config.get("min"), "min").getDouble();
            }
            if (config.containsKey("max"))
            {
                max = NumericValue.asNumber(config.get("max"), "max").getDouble();
            }
            if (max != null && min == null) throw new InternalExpressionException("Double types cannot be only upper-bounded");
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return FloatArgument::new;
        }
    }

    private static class IntArgument extends CommandArgument
    {
        private Long min = null;
        private Long max = null;
        private IntArgument()
        {
            super("int", LongArgumentType.longArg().getExamples(), true);
        }

        @Override
        public ArgumentType<?> getArgumentType()
        {
            if (min != null)
            {
                if (max != null)
                {
                    return LongArgumentType.longArg(min, max);
                }
                return LongArgumentType.longArg(min);
            }
            return LongArgumentType.longArg();
        }

        @Override
        public Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            return new NumericValue(LongArgumentType.getLong(context, param));
        }

        @Override
        protected void configure(Map<String, Value> config)
        {
            super.configure(config);
            if (config.containsKey("min"))
            {
                min = NumericValue.asNumber(config.get("min"), "min").getLong();
            }
            if (config.containsKey("max"))
            {
                max = NumericValue.asNumber(config.get("max"), "max").getLong();
            }
            if (max != null && min == null) throw new InternalExpressionException("Double types cannot be only upper-bounded");
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return FloatArgument::new;
        }
    }

    @FunctionalInterface
    private interface ValueExtractor
    {
        Value apply(CommandContext<ServerCommandSource> ctx, String param) throws CommandSyntaxException;
    }

    public static class VanillaUnconfigurableArgument extends  CommandArgument
    {
        private Supplier<ArgumentType<?>> argumentTypeSupplier;
        private ValueExtractor valueExtractor;
        private boolean providesExamples;
        public VanillaUnconfigurableArgument(
                String suffix,
                Supplier<ArgumentType<?>> argumentTypeSupplier,
                ValueExtractor  valueExtractor,
                boolean providesExamples
                )
        {
            super(suffix, argumentTypeSupplier.get().getExamples(), providesExamples);
            this.providesExamples = providesExamples;
            this.argumentTypeSupplier = argumentTypeSupplier;
            this.valueExtractor = valueExtractor;
        }

        @Override
        protected ArgumentType<?> getArgumentType()
        {
            return argumentTypeSupplier.get();
        }

        @Override
        protected Value getValueFromContext(CommandContext<ServerCommandSource> context, String param) throws CommandSyntaxException
        {
            return valueExtractor.apply(context, param);
        }

        @Override
        protected Supplier<CommandArgument> builder()
        {
            return () -> new VanillaUnconfigurableArgument(getTypeSuffix(), argumentTypeSupplier, valueExtractor, providesExamples);
        }
    }
}
