package com.moe365.mopi;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.moe365.mopi.CommandLineParser.ParsedCommandLineArguments;

/**
 * Utility class to parse a command line arguments passed to the jar.
 * @author mailmindlin
 */
public class CommandLineParser implements Serializable, Function<String[], ParsedCommandLineArguments> {
	private static final long serialVersionUID = 4501136312997123150L;
	/**
	 * The message object to display how to use the command. Currently not implemented.
	 */
	protected CommandLineUsage usage = new CommandLineUsage();
	/**
	 * The stored option (i.e., flag) signatures
	 */
	protected HashMap<String, CommandLineToken> options;
	/**
	 * A map generated of all of the aliases and their mappings, for faster lookup
	 */
	protected transient HashMap<String, Set<String>> aliases;
	
	/**
	 * Create a new builder for a command line parser with options.
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Constructor for deserialization
	 */
	protected CommandLineParser() {
		
	}
	
	protected CommandLineParser(HashMap<String, CommandLineToken> options) {
		this.options = options;
	}
	
	public CommandLineParser(Builder builder) {
		this.options = new HashMap<>(builder.options);
	}

	/**
	 * Build a help string with all the aliases and stuff
	 * @return a help string, printable 
	 */
	public String getHelpString() {
		StringBuilder result = new StringBuilder();
		//add usage
		result.append("Usage: ").append(usage).append('\n');
		
		for (Map.Entry<String, CommandLineToken> entry : this.options.entrySet().parallelStream().sorted((a,b)->(a.getKey().compareTo(b.getKey()))).collect(Collectors.toList())) {
			CommandLineToken token = entry.getValue();
			if (token == null) {
				System.err.println("Null under " + entry.getKey());
				continue;
			}
			if (token.getType() == CommandLineTokenType.ALIAS)
				continue;
			Set<String> aliases = getAliasesFor(entry.getKey());
			if (token.getType() == CommandLineTokenType.KV_PAIR) {
				CommandLineKVPair kvToken = (CommandLineKVPair) token;
				for (String alias : aliases)
					result.append("  ").append(alias)
						.append(" [").append(kvToken.getFieldName()).append("]\n");
				result.append("  ").append(entry.getKey()).append(" [").append(kvToken.getFieldName()).append("]\n");
			} else {
				for (String alias : aliases)
					result.append("  ").append(alias).append('\n');
				result.append("  ").append(entry.getKey()).append('\n');
			}
			result.append("    ")
				.append(token.getDescription().replace("\n", "\n    "))
				.append('\n');
		}
		
		return result.toString();
	}
	
	/**
	 * Apply to the argument array
	 * @param args
	 * @return
	 */
	@Override
	public ParsedCommandLineArguments apply(String[] args) {
		HashMap<String, String> data = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			CommandLineToken token = this.options.get(args[i]);
			if (token == null) {
				System.err.println("Unknown token: " + args[i]);
				data.putIfAbsent(args[i], "");
				continue;
			}
			
			while (token.getType() == CommandLineTokenType.ALIAS)//TODO fix infinite loops
				token = options.get(((CommandLineAlias)token).getTarget());
			
			if (token.getType() == CommandLineTokenType.FLAG)
				data.put(token.getName(), "true");
			if (token.getType() == CommandLineTokenType.KV_PAIR)
				data.put(token.getName(), args[++i]);
		}
		return new ParsedCommandLineArguments(data);
	}
	
	/**
	 * Get the set of aliases that are mapped to a given name.
	 * Mostly used for building the help string
	 * @param name Name (of real command) to get aliases for
	 * @return Set of aliases for command, or empty set if invalid command or no aliases exist
	 */
	@SuppressWarnings("unchecked")
	public Set<String> getAliasesFor(String name) {
		if (this.aliases == null) {
			//build alias map
			synchronized (this) {
				//Check again once lock is acquired
				if (this.aliases == null) {
					//Build alias map
					HashMap<String, Set<String>> tmp = new HashMap<>();
					for (CommandLineToken token : options.values()) {
						if (token == null || token.getType() != CommandLineTokenType.ALIAS)
							continue;
						CommandLineAlias alias = (CommandLineAlias) token; 
						tmp.computeIfAbsent(alias.getTarget(), x->new HashSet<String>()).add(alias.getName());
					}
					this.aliases = tmp;
				}
			}
		}
		return this.aliases.getOrDefault(name, Collections.EMPTY_SET);
	}
	
	/**
	 * A map of the command line arguments to their given values, with
	 * features such as conversion between primitive types
	 */
	public class ParsedCommandLineArguments {
		protected final HashMap<String, String> data;
		
		protected ParsedCommandLineArguments(HashMap<String, String> data) {
			this.data = data;
		}
		
		/**
		 * Returns whether the specified flag or option has been set. Does not
		 * support aliases such that if <code>foo</code> is an alias for
		 * <code>bar</code>, and <code>foo</code> is set in the arguments,
		 * <code>isFlagSet("bar")==true</code>, while
		 * <code>isFlagSet("foo")==false</code>
		 * 
		 * @param name
		 *            Name of the flag or option to detect
		 * @return Whether the specified flag or an option has been set
		 */
		public boolean isFlagSet(String name) {
			return data.containsKey(name);
		}
		
		/**
		 * Get the value of the given option. Returns null if the queried name is a flag.
		 * @param name The name of the option
		 * @return The value of the option, or null if the option is not set
		 */
		public String get(String name) {
			return data.get(name);
		}
		
		/**
		 * Get the value of an option, or 
		 * @param name
		 * @param def
		 * @return
		 */
		public String getOrDefault(String name, String def) {
			if (isFlagSet(name))
				return get(name);
			return def;
		}
		/**
		 * Get the value for the given key if set, or the default value if not set,
		 * or the value cannot be parsed as an integer.
		 * @param name The name of the option
		 * @param def A default value if the option is not set, or is not an integer
		 * @return The integer value of the given key, or the default value
		 */
		public int getOrDefault(String name, int def) {
			if (isFlagSet(name)) {
				try {
					return Integer.parseInt(get(name));
				} catch (NumberFormatException e){
					//It's ok. Fallback to default value
				}
			}
			return def;
		}
		
		/**
		 * Combine the two ParsedCommandLineArguments objects. Is similar to combining
		 * maps via {@link Map#putAll(Map)}. Not sure why one would want this feature, but
		 * here it is.
		 * @param t Other values to append
		 * @return self
		 */
		public ParsedCommandLineArguments add(ParsedCommandLineArguments t) {
			this.data.putAll(t.data);
			return this;
		}
		
	}
	/**
	 * Builder for CommandLineParser's
	 * @author mailmindlin
	 */
	public static class Builder {
		protected HashMap<String, CommandLineToken> options = new HashMap<>();
		/**
		 * Creates an empty Builder
		 */
		public Builder() {
			
		}
		
		/**
		 * Creates a clone of a given builder
		 * @param src The builder to clone
		 */
		public Builder(Builder src) {
			this.options = new HashMap<>(src.options);
		}
		
		/**
		 * Makes a clone of this Builder, if you want to do that for some reason.
		 * @return self
		 */
		public Builder clone() {
			return new Builder(this);
		}
		
		/**
		 * Add a boolean flag.
		 * @param name The flag's name
		 * @param description A description of what the flag does
		 * @return self
		 */
		public Builder addFlag(String name, String description) {
			options.put(name, new CommandLineFlag(name, description));
			return this;
		}
		
		/**
		 * Add alias.
		 * @param from The name of the alias to create
		 * @param to The name of the flag/option to alias
		 * @return self
		 */
		public Builder alias(String from, String to) {
			options.put(from, new CommandLineAlias(from, to));
			return this;
		}
		
		/**
		 * Register a key-value pair. Key-value pair flags are flags in the format of
		 * <kbd>--flag [value]</kbd>. 
		 * @param name The name of the flag (what is used to set this, including preceding dashes)
		 * @param argName the name of the value (for description only)
		 * @param description A (short) description of what the flag does
		 * @return self
		 */
		public Builder addKvPair(String name, String argName, String description) {
			options.put(name, new CommandLineKVPair(name, argName, description));
			return this;
		}
		
		/**
		 * Builds a CommandLineParser from the data given to this builder
		 * @return built object
		 */
		public CommandLineParser build() {
			return new CommandLineParser(this);
		}
	}
	
	public class CommandLineUsage implements Serializable {
		private static final long serialVersionUID = -1994891773152646790L;
		//TODO finish
		@Override
		public String toString() {
			return "java -jar MoePi.jar [options]";
		}
	}
	
	/**
	 * The type of command line token.
	 * @author mailmindlin
	 */
	public enum CommandLineTokenType {
		/**
		 * Alias token type, which maps directly to another token
		 */
		ALIAS,
		/**
		 * Flag token type, which can be tested for if it is set or not
		 */
		FLAG,
		/**
		 * A key-value pair type, which can be tested if it exists, and what value it
		 * is set to.
		 */
		KV_PAIR
	}
	
	/**
	 * A token to search for in the command line options.
	 * @author mailmindlin
	 *
	 */
	public interface CommandLineToken extends Externalizable {
		/**
		 * 
		 * @return the name of the command
		 */
		String getName();

		/**
		 * A description of how the command may be used. Given as part of the
		 * help string.
		 * 
		 * @return description string
		 */
		String getDescription();
		
		/**
		 * @return the type of command
		 * @see CommandLineTokenType
		 */
		CommandLineTokenType getType();
	}
	
	/**
	 * An alias of another token
	 * @author mailmindlin
	 */
	public static class CommandLineAlias implements CommandLineToken {
		/**
		 * Name of alias
		 */
		protected String name;
		/**
		 * Name of target
		 */
		protected String targetName;
		
		/**
		 * Constructor for deserialization
		 */
		protected CommandLineAlias() {
			
		}
		
		/**
		 * Create an alias for the given command
		 * @param name Name of the alias
		 * @param target Name of the command to alias
		 */
		public CommandLineAlias(String name, String target) {
			this.name = name;
			this.targetName = target;
		}
		
		/**
		 * @return name of the alias
		 */
		@Override
		public String getName() {
			return this.name;
		}
		
		/**
		 * @return Name of the command being aliased by this
		 */
		public String getTarget() {
			return this.targetName;
		}

		@Override
		public String getDescription() {
			return "";
		}

		@Override
		public CommandLineTokenType getType() {
			return CommandLineTokenType.ALIAS;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(name);
			out.writeUTF(targetName);
		}
		
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			name = in.readUTF();
			targetName = in.readUTF();
		}
	}
	
	/**
	 * A flag type. Can be queried by name to determine if it has been set. Has no value.
	 * @author mailmindlin
	 */
	public static class CommandLineFlag implements CommandLineToken {
		/**
		 * Name of the flag
		 */
		protected String name;
		/**
		 * Description string
		 */
		protected String description;
		
		/**
		 * Constructor for deserialization
		 */
		public CommandLineFlag() {
			
		}
		
		/**
		 * 
		 * @param name
		 *            Name of the flag, as it will appear in the parameters.
		 *            Must include all preceding dashes
		 * @param description
		 *            Optional (short) description string, describing the use of
		 *            this flag
		 */
		public CommandLineFlag(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String getName() {
			return this.name;
		}
		
		@Override
		public String getDescription() {
			return this.description;
		}
		
		@Override
		public CommandLineTokenType getType() {
			return CommandLineTokenType.FLAG;
		}
		
		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(getName());
			out.writeUTF(getDescription());
		}
		
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			name = in.readUTF();
			description = in.readUTF();
		}
	}
	public static class CommandLineKVPair implements CommandLineToken {
		protected String name;
		protected String fieldName;
		protected String description;
		
		public CommandLineKVPair() {
			
		}
		
		/**
		 * @param name Name of the key (used in the string). Must include all preceding dashes
		 * @param fieldName Name of the field. Optional, but used in the help string
		 * @param description A short description of how to use this option
		 */
		public CommandLineKVPair(String name, String fieldName, String description) {
			this.name = name;
			this.fieldName = fieldName;
			this.description = description;
		}
		
		/**
		 * Get the name of the key
		 */
		@Override
		public String getName() {
			return this.name;
		}
		
		/**
		 * Get the name of the field, mostly used for the help string.
		 * @return name of the field, or "value" if no field name was given
		 * @see #fieldName
		 */
		public String getFieldName() {
			return this.fieldName == null ? "value" : this.fieldName;
		}
		
		@Override
		public String getDescription() {
			return this.description;
		}
		
		@Override
		public CommandLineTokenType getType() {
			return CommandLineTokenType.KV_PAIR;
		}
		
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			this.name = in.readUTF();
			this.description = in.readUTF();
			this.fieldName = in.readUTF();
		}
		
		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(name);
			out.writeUTF(description);
			out.writeUTF(fieldName);
		}
		
	}
}
