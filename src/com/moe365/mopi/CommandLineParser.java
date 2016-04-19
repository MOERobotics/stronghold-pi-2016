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
	protected CommandLineUsage usage = new CommandLineUsage();
	protected HashMap<String, CommandLineToken> options;
	protected transient HashMap<String, Set<String>> aliases;
	
	public static Builder builder() {
		return new Builder();
	}
	
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
	
	@SuppressWarnings("unchecked")
	public Set<String> getAliasesFor(String name) {
		if (this.aliases == null) {
			//build alias map
			synchronized (this) {
				HashMap<String, Set<String>> tmp = new HashMap<>();
				for (CommandLineToken token : options.values()) {
					if (token == null || token.getType() != CommandLineTokenType.ALIAS)
						continue;
					CommandLineAlias alias = (CommandLineAlias) token; 
					tmp.computeIfAbsent(alias.getTarget(), (x)->(new HashSet<String>())).add(alias.name);
				}
				this.aliases = tmp;
			}
		}
		return this.aliases.getOrDefault(name, Collections.EMPTY_SET);
	}
	
	public class ParsedCommandLineArguments {
		HashMap<String, String> data;
		protected ParsedCommandLineArguments(HashMap<String, String> data) {
			this.data = data;
		}
		public boolean isFlagSet(String name) {
			return data.containsKey(name);
		}
		public String get(String name) {
			return data.get(name);
		}
		public String getOrDefault(String name, String def) {
			if (isFlagSet(name))
				return get(name);
			return def;
		}
		public int getOrDefault(String name, int def) {
			if (isFlagSet(name)) {
				try {
					return Integer.parseInt(get(name));
				} catch (Exception e){}
			}
			return def;
		}
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
		public Builder() {
			
		}
		public Builder(Builder src) {
			this.options = new HashMap<>(src.options);
		}
		public Builder clone() {
			return new Builder(this);
		}
		public Builder addFlag(String name, String description) {
			options.put(name, new CommandLineFlag(name, description));
			return this;
		}
		/**
		 * Add alias.
		 * @param from alias name
		 * @param to what to alias to
		 * @return self
		 */
		public Builder alias(String from, String to) {
			options.put(from, new CommandLineAlias(from, to));
			return this;
		}
		public Builder addKvPair(String name, String argName, String description) {
			options.put(name, new CommandLineKVPair(name, argName, description));
			return this;
		}
		public CommandLineParser build() {
			return new CommandLineParser(this);
		}
	}
	
	public class CommandLineUsage implements Serializable {
		private static final long serialVersionUID = -1994891773152646790L;
		//TODO finish
	}
	
	/**
	 * @author mailmindlin
	 */
	public enum CommandLineTokenType {
		ALIAS,
		FLAG,
		KV_PAIR
	}
	
	public interface CommandLineToken extends Externalizable {
		String getName();
		String getDescription();
		CommandLineTokenType getType();
	}
	public static class CommandLineAlias implements CommandLineToken {
		/**
		 * Name of alias
		 */
		protected String name;
		/**
		 * Name of target
		 */
		protected String targetName;
		
		protected CommandLineAlias() {
			
		}
		
		public CommandLineAlias(String name, String target) {
			this.name = name;
			this.targetName = target;
		}
		
		@Override
		public String getName() {
			return targetName;
		}
		
		public String getTarget() {
			return targetName;
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
	public static class CommandLineFlag implements CommandLineToken {
		protected String name;
		protected String description;
		public CommandLineFlag() {
			
		}
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
		protected CommandLineKVPair() {
			
		}
		public CommandLineKVPair(String name, String fieldName, String description) {
			this.name = name;
			this.fieldName = fieldName;
			this.description = description;
		}
		@Override
		public String getName() {
			return this.name;
		}

		public String getFieldName() {
			return this.fieldName;
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
			name = in.readUTF();
			description = in.readUTF();
			fieldName = in.readUTF();
		}
		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(name);
			out.writeUTF(description);
			out.writeUTF(fieldName);
		}
		
	}
}
